package pkqb.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import pkqb.common.Result;
import pkqb.config.DashScopeModelFactory;
import pkqb.pojo.dto.AiRubric;
import pkqb.pojo.entity.ModelsEntity;
import pkqb.service.NotificationService;
import pkqb.service.OssService;
import pkqb.service.UserApiKeyService;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 视觉模型题目解析全链路（自 SpringAiAlibabaServiceImpl 拆分）：
 * PDF页面渲染与嵌入图片提取 → OSS并行上传 → 分片并发调用视觉模型 → 跨页题合并与图片URL映射。
 * 任一环节失败时降级回调主服务（SpringAiAlibabaServiceImpl）的纯文本解析流程；
 * 共享线程池 AI_EXECUTOR 与静态工具方法统一放在 {@link AiCommonSupport}，全局仅一份。
 */
@Component
@Slf4j
public class RubricVisionParser {

    private final ObjectMapper objectMapper;
    private final UserApiKeyService userApiKeyService;
    private final DashScopeModelFactory dashScopeModelFactory;
    private final OssService ossService;
    private final NotificationService notificationService;
    /** 主服务：视觉解析失败时降级调用其纯文本解析流程（@Lazy 打破构造器循环依赖） */
    private final SpringAiAlibabaServiceImpl owner;

    /** 视觉模型分片解析：每片页数 */
    private static final int VISION_CHUNK_PAGES = 2;
    /** 视觉模型分片解析：并发调用上限（避免触发模型服务限流） */
    private static final int VISION_CONCURRENCY = 3;
    private static final java.util.concurrent.Semaphore VISION_SEMAPHORE =
            new java.util.concurrent.Semaphore(VISION_CONCURRENCY);

    public RubricVisionParser(ObjectMapper objectMapper,
                              UserApiKeyService userApiKeyService,
                              DashScopeModelFactory dashScopeModelFactory,
                              OssService ossService,
                              NotificationService notificationService,
                              @Lazy SpringAiAlibabaServiceImpl owner) {
        this.objectMapper = objectMapper;
        this.userApiKeyService = userApiKeyService;
        this.dashScopeModelFactory = dashScopeModelFactory;
        this.ossService = ossService;
        this.notificationService = notificationService;
        this.owner = owner;
    }

    /**
     * 使用视觉模型解析PDF文件：PDF页面渲染为图片→上传OSS→URL传给视觉模型→结构化输出+嵌入图片提取映射
     */
    Result<List<AiRubric>> handleRubricFileWithVision(MultipartFile file, Long userId,
                                                      java.util.function.Consumer<String> progress) {
        // 提到 try 外，便于降级时清理已上传的 OSS 图片
        Map<Integer, String> pageUrlByIndex = null;
        List<ExtractedImage> extractedImages = null;
        try {
            log.info("[题目文件解析-视觉] 使用视觉模型解析PDF文件");
            byte[] fileBytes = file.getBytes();

            // Step 1: 串行渲染页面 + 提取嵌入图片（共享 PDDocument，PDFBox 非线程安全）
            if (progress != null) progress.accept("正在渲染PDF页面并提取图片");
            List<PageImage> pageImages;
            try (PDDocument document = Loader.loadPDF(fileBytes)) {
                pageImages = renderPdfPagesToBytes(document);
                extractedImages = extractImagesFromPdfToBytes(document);
            }

            if (pageImages.isEmpty()) {
                log.warn("[题目文件解析-视觉] PDF页面渲染失败，降级到纯文本模式");
                return owner.handleRubricFileWithText(file, userId, "PDF页面渲染失败", progress);
            }
            log.info("[题目文件解析-视觉] PDF渲染为 {} 张页面图片，提取到 {} 张嵌入图片",
                    pageImages.size(), extractedImages.size());

            // Step 2: 并行上传所有图片到 OSS
            if (progress != null) {
                progress.accept("正在上传 " + (pageImages.size() + extractedImages.size()) + " 张图片");
            }
            pageUrlByIndex = uploadAllImagesParallel(pageImages, extractedImages, userId);
            if (pageUrlByIndex.isEmpty()) {
                log.warn("[题目文件解析-视觉] 全部页面图片上传失败，降级到纯文本模式");
                cleanupOssImages(new ArrayList<>(pageUrlByIndex.values()), extractedImages);
                return owner.handleRubricFileWithText(file, userId, "页面图片上传失败", progress);
            }

            // Step 3: 分片并发调用视觉模型，获取结构化题目并合并
            if (progress != null) progress.accept("AI解析中");
            List<AiRubric> aiRubrics = callVisionModelForQuestions(pageUrlByIndex, extractedImages, userId, progress);
            if (aiRubrics == null || aiRubrics.isEmpty()) {
                log.warn("[题目文件解析-视觉] 视觉模型返回为空，降级到纯文本模式");
                cleanupOssImages(new ArrayList<>(pageUrlByIndex.values()), extractedImages);
                return owner.handleRubricFileWithText(file, userId, "视觉模型返回结果为空", progress);
            }

            // Step 4: 将图片编号映射为实际URL
            mapImageReferencesToUrls(aiRubrics, extractedImages);

            log.info("[题目文件解析-视觉] 解析完成，共提取 {} 道题目", aiRubrics.size());
            notificationService.notifyRubricParseComplete(userId, "视觉模型");
            return Result.success(aiRubrics);
        } catch (Exception e) {
            log.error("[题目文件解析-视觉] 视觉识别失败: {}，降级到纯文本模式", e.getMessage());
            cleanupOssImages(pageUrlByIndex == null ? null : new ArrayList<>(pageUrlByIndex.values()), extractedImages);
            return owner.handleRubricFileWithText(file, userId, e.getMessage(), progress);
        }
    }

    /**
     * 将PDF每页渲染为JPEG字节数组（DPI=115），不上传OSS。
     * 渲染阶段保持串行：PDFBox 的 PDFRenderer 对同一 PDDocument 非线程安全。
     */
    private List<PageImage> renderPdfPagesToBytes(PDDocument document) {
        List<PageImage> images = new ArrayList<>();
        try {
            PDFRenderer renderer = new PDFRenderer(document);
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                BufferedImage pageImage = renderer.renderImageWithDPI(i, 115);
                byte[] imageBytes = writeJpeg(pageImage, 0.7f);
                images.add(new PageImage(i, imageBytes));
            }
        } catch (Exception e) {
            log.error("[PDF页面渲染] 渲染PDF页面失败: {}", e.getMessage());
        }
        return images;
    }

    /**
     * 将 BufferedImage 写入 JPEG 字节数组。
     * 自动把带 alpha 通道的图片转为白底 RGB，避免 JPEG 编码异常/背景变黑。
     */
    private byte[] writeJpeg(BufferedImage image, float quality) throws IOException {
        BufferedImage rgb = flattenToRgb(image);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.setOutput(ImageIO.createImageOutputStream(baos));
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }

    private BufferedImage flattenToRgb(BufferedImage src) {
        if (!src.getColorModel().hasAlpha()) {
            return src;
        }
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, src.getWidth(), src.getHeight());
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    /**
     * 若图片最长边超过 maxEdge，等比缩小到 maxEdge；否则原样返回。
     * 视觉模型内部会把输入图缩到自身工作分辨率，超限像素只增加传输耗时。
     */
    private BufferedImage scaleDownToMaxEdge(BufferedImage src, int maxEdge) {
        int w = src.getWidth();
        int h = src.getHeight();
        int longest = Math.max(w, h);
        if (longest <= maxEdge) {
            return src;
        }
        double scale = (double) maxEdge / longest;
        int nw = (int) Math.round(w * scale);
        int nh = (int) Math.round(h * scale);
        BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, nw, nh);
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(src, 0, 0, nw, nh, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    /** 渲染后的页面图片字节（未上传） */
    private static class PageImage {
        final int pageIndex;
        final byte[] imageBytes;

        PageImage(int pageIndex, byte[] imageBytes) {
            this.pageIndex = pageIndex;
            this.imageBytes = imageBytes;
        }
    }

    /**
     * 单片解析结果：题目列表 + 首尾题跨页标记
     */
    private static class ChunkParseResult {
        final List<Integer> pageIndices;
        final List<AiRubric> questions;
        /** 首题题干是否从上一片最后一页延续而来 */
        final boolean firstQuestionIsContinuation;
        /** 尾题是否在本片内完整结束 */
        final boolean lastQuestionComplete;

        ChunkParseResult(List<Integer> pageIndices, List<AiRubric> questions,
                         boolean firstQuestionIsContinuation, boolean lastQuestionComplete) {
            this.pageIndices = pageIndices;
            this.questions = questions;
            this.firstQuestionIsContinuation = firstQuestionIsContinuation;
            this.lastQuestionComplete = lastQuestionComplete;
        }
    }

    /**
     * 分片并发调用视觉模型：按页分片、限流并发、单片失败重试一次，最后按片顺序合并并处理跨页题。
     * 分片后单片输出 token 大幅减少，避免超长输出被 max_tokens 截断。
     */
    private List<AiRubric> callVisionModelForQuestions(Map<Integer, String> pageUrlByIndex,
                                                       List<ExtractedImage> extractedImages,
                                                       Long userId,
                                                       java.util.function.Consumer<String> progress) {
        try {
            List<Integer> allPages = new ArrayList<>(pageUrlByIndex.keySet());
            // 分片：每 VISION_CHUNK_PAGES 页一片
            List<List<Integer>> chunks = new ArrayList<>();
            for (int i = 0; i < allPages.size(); i += VISION_CHUNK_PAGES) {
                chunks.add(new ArrayList<>(allPages.subList(i, Math.min(i + VISION_CHUNK_PAGES, allPages.size()))));
            }
            log.info("[题目文件解析-视觉] 共 {} 页，分为 {} 片并发调用（并发上限 {}）",
                    allPages.size(), chunks.size(), VISION_CONCURRENCY);

            // 视觉模型只构建一次：此前每个分片内都重新查Key/查模型并new ChatModel，浪费查询且拖慢并发
            String apiKey = userApiKeyService.hasUserOwnApiKey(userId) ? userApiKeyService.getPlainApiKey(userId) : null;
            ModelsEntity visionModel = userApiKeyService.getVisionModel(userId);
            String modelName = visionModel != null ? visionModel.getModelName() : null;
            ChatModel sharedVisionModel = dashScopeModelFactory.createVisionChatModel(
                    apiKey != null ? apiKey : dashScopeModelFactory.getDefaultApiKey(),
                    modelName);

            // 并发调用单片（信号量限流），失败自动重试一次；每片完成时上报进度
            java.util.concurrent.atomic.AtomicInteger completedChunks = new java.util.concurrent.atomic.AtomicInteger();
            List<CompletableFuture<ChunkParseResult>> futures = new ArrayList<>();
            for (List<Integer> chunk : chunks) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        VISION_SEMAPHORE.acquire();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                    try {
                        ChunkParseResult result = callVisionModelForChunk(chunk, pageUrlByIndex, extractedImages, sharedVisionModel);
                        if (result == null) {
                            log.warn("[题目文件解析-视觉] 分片 {} 解析失败，重试一次", chunk);
                            result = callVisionModelForChunk(chunk, pageUrlByIndex, extractedImages, sharedVisionModel);
                        }
                        return result;
                    } finally {
                        VISION_SEMAPHORE.release();
                    }
                }, AiCommonSupport.AI_EXECUTOR).thenApply(r -> {
                    int done = completedChunks.incrementAndGet();
                    if (progress != null) {
                        progress.accept("AI解析中 " + done + "/" + chunks.size() + " 片");
                    }
                    return r;
                }).exceptionally(ex -> {
                    log.warn("[题目文件解析-视觉] 分片 {} 调用异常: {}", chunk, ex.getMessage());
                    return null;
                }));
            }

            List<ChunkParseResult> chunkResults = new ArrayList<>();
            int failed = 0;
            for (CompletableFuture<ChunkParseResult> f : futures) {
                ChunkParseResult r = f.join();
                if (r != null) {
                    chunkResults.add(r);
                } else {
                    failed++;
                }
            }
            if (failed > 0) {
                log.warn("[题目文件解析-视觉] {}/{} 个分片解析失败，返回成功分片结果", failed, chunks.size());
            }
            if (chunkResults.isEmpty()) return null;

            return mergeChunkResults(chunkResults, pageUrlByIndex, extractedImages, sharedVisionModel, progress);
        } catch (Exception e) {
            log.error("[题目文件解析-视觉] 分片并发调用失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 按片顺序合并题目。相邻片边界出现跨页题（前片尾题不完整且后片首题为延续）时，
     * 用"前片最后一页 + 后片第一页"作为补漏分片重调一次模型，替换两个半题。
     * 两标记矛盾时不做补漏、全部保留（信任"完整"一方，避免误删）。
     */
    private List<AiRubric> mergeChunkResults(List<ChunkParseResult> chunkResults,
                                             Map<Integer, String> pageUrlByIndex,
                                             List<ExtractedImage> extractedImages,
                                             ChatModel sharedVisionModel,
                                             java.util.function.Consumer<String> progress) {
        List<AiRubric> merged = new ArrayList<>();
        boolean prevTailIncomplete = false;
        List<Integer> prevChunkPages = null;
        AiRubric prevTailQuestion = null;

        for (ChunkParseResult chunk : chunkResults) {
            if (chunk.questions.isEmpty()) continue;

            if (prevTailIncomplete && chunk.firstQuestionIsContinuation && prevChunkPages != null) {
                // 确认跨页题：移除已入列的前片尾题半题，丢弃本片首题半题，补漏边界两页
                if (!merged.isEmpty()) {
                    merged.remove(merged.size() - 1);
                }
                int curFirst = chunk.pageIndices.get(0);
                int prevLast = prevChunkPages.get(prevChunkPages.size() - 1);
                if (curFirst == prevLast) {
                    // 边界页相同（极端情况：片只含1页），跳过补漏避免重复调用同一页
                    log.warn("[题目文件解析-视觉] 边界页重复（页{}），保留原始两半题", curFirst);
                    merged.add(prevTailQuestion);
                    merged.addAll(chunk.questions);
                } else {
                    List<Integer> gapPages = List.of(prevLast, curFirst);
                    log.info("[题目文件解析-视觉] 检测到跨页题，补漏解析边界页 {}", gapPages);
                    if (progress != null) progress.accept("检测到跨页题，正在补漏解析边界页");
                    ChunkParseResult gap = callVisionModelForChunk(gapPages, pageUrlByIndex, extractedImages, sharedVisionModel);
                    List<AiRubric> gapQuestions = null;
                    if (gap != null && !gap.questions.isEmpty()) {
                        gapQuestions = new ArrayList<>(gap.questions);
                        if (gap.firstQuestionIsContinuation) {
                            log.warn("[题目文件解析-视觉] 补漏分片首题仍为延续（题目跨3页以上），丢弃该不完整题: 页{}", gapPages);
                            gapQuestions.remove(0);
                        }
                        if (!gap.lastQuestionComplete && !gapQuestions.isEmpty()) {
                            log.warn("[题目文件解析-视觉] 补漏分片尾题不完整（题目跨3页以上），丢弃该不完整题: 页{}", gapPages);
                            gapQuestions.remove(gapQuestions.size() - 1);
                        }
                    }
                    if (gapQuestions != null && !gapQuestions.isEmpty()) {
                        merged.addAll(gapQuestions);
                    } else {
                        // 补漏失败：保留两个半题，宁多勿缺
                        log.warn("[题目文件解析-视觉] 补漏分片无有效结果，保留边界两个不完整题");
                        merged.add(prevTailQuestion);
                    }
                    merged.addAll(chunk.questions.subList(1, chunk.questions.size()));
                }
            } else {
                if (prevTailIncomplete && prevChunkPages != null) {
                    log.warn("[题目文件解析-视觉] 相邻片跨页标记矛盾，保留全部题目以避免误删");
                }
                merged.addAll(chunk.questions);
            }

            prevTailIncomplete = !chunk.lastQuestionComplete;
            prevChunkPages = chunk.pageIndices;
            prevTailQuestion = chunk.questions.get(chunk.questions.size() - 1);
        }
        if (prevTailIncomplete && prevTailQuestion != null) {
            log.warn("[题目文件解析-视觉] 最后一片尾题不完整（题目可能延续到缺失页），保留现有内容");
        }
        return merged;
    }

    /**
     * 调用视觉模型解析一个分片（1~2页），返回题目与首尾跨页标记。
     * 视觉模型由调用方构建一次后传入，避免分片循环内重复查库建模型。
     */
    private ChunkParseResult callVisionModelForChunk(List<Integer> chunkPages,
                                                     Map<Integer, String> pageUrlByIndex,
                                                     List<ExtractedImage> extractedImages,
                                                     ChatModel chatModel) {
        try {
            // 本片页码范围内已成功上传的嵌入图片（编号为全文档统一编号，与最终URL映射一致）
            int minPage = chunkPages.get(0);
            int maxPage = chunkPages.get(chunkPages.size() - 1);
            StringBuilder imageRef = new StringBuilder();
            for (ExtractedImage img : extractedImages) {
                if (img.url != null && img.pageIndex >= minPage && img.pageIndex <= maxPage) {
                    imageRef.append("- 图片").append(img.index).append("\n");
                }
            }

            String pageDesc = chunkPages.size() == 1
                    ? "第" + (chunkPages.get(0) + 1) + "页"
                    : "第" + (chunkPages.get(0) + 1) + "页到第" + (chunkPages.get(chunkPages.size() - 1) + 1) + "页";

            String promptText = """
                你是一个专业的题目解析专家。以下是一份试卷的%s图片，请从中提取所有题目，输出为JSON对象。

                要求：
                1. 输出格式为：{"questions": [题目数组], "firstQuestionIsContinuation": 布尔值, "lastQuestionIsComplete": 布尔值}
                2. questions数组中每道题包含：question（题目内容）、questionType（题型）、options（选项，没有则为空数组）、answer（答案）、explanation（解析）、calculationSteps（计算步骤，没有则为空数组）、resources（图片资源）
                3. resources格式为[{"url":"图片N","type":"资源类型","label":"标签"}]，N为图片编号
                   type必须为以下之一：
                   - question_image：题目本身的配图
                   - option_image：选项配图（必须同时填写label为对应选项字母，如"A"、"B"、"C"、"D"）
                   - answer_image：答案中的配图
                   - explanation_image：解析中的配图
                4. 如果题目中包含图片，在question字段中用[图片N]标记图片位置，同时在resources中添加对应引用
                5. firstQuestionIsContinuation：本片第一道题的题干是否从上一页延续而来（即题干开头不在本片第一页）。若第一道题从本片开始则为false
                6. lastQuestionIsComplete：本片最后一道题是否在本片内完整结束（题干、选项未被截断）。若最后一道题延续到下一页则为false
                7. 严格输出JSON对象，不要输出其他内容
                8. 数学内容禁止使用LaTeX语法（如\\angle、^\\circ、\\frac、$...$），必须直接使用Unicode字符：
                   角度用∠、度数用°、乘号用×、除号用÷、不等于用≠、小于等于用≤、大于等于用≥、约等于用≈、根号用√、圆周率用π、三角形用△；分数写成a/b，幂写成x²
                """.formatted(pageDesc)
                    + (imageRef.isEmpty() ? "" : "\n\n本片页面包含以下嵌入图片：\n" + imageRef);

            // 视觉模型已由调用方统一构建并传入（见 callVisionModelForQuestions）

            // 构建包含本片页面图片的请求
            var userSpec = ChatClient.builder(chatModel).build().prompt()
                    .user(u -> {
                        u.text(promptText);
                        for (Integer pageIndex : chunkPages) {
                            String url = pageUrlByIndex.get(pageIndex);
                            if (url == null) continue;
                            try {
                                u.media(org.springframework.util.MimeTypeUtils.IMAGE_JPEG,
                                        new org.springframework.core.io.UrlResource(url));
                            } catch (java.net.MalformedURLException e) {
                                log.warn("[题目文件解析-视觉] 图片URL格式错误: {}", url);
                            }
                        }
                    });

            String response = userSpec.call().content();
            log.info("[题目文件解析-视觉] 分片 {} 返回，响应长度: {}", chunkPages,
                    response != null ? response.length() : 0);

            ChunkParseResult result = parseChunkResponse(response, chunkPages);
            if (result != null) {
                AiCommonSupport.sanitizeRubricMath(result.questions);
            }
            return result;
        } catch (Exception e) {
            log.error("[题目文件解析-视觉] 分片 {} 调用失败: {}", chunkPages, e.getMessage());
            return null;
        }
    }

    /**
     * 解析分片响应：优先按 {"questions": [...], "firstQuestionIsContinuation": ..., "lastQuestionIsComplete": ...} 对象格式；
     * 模型未按格式输出裸数组时，按数组解析并默认首尾完整。
     */
    private ChunkParseResult parseChunkResponse(String response, List<Integer> chunkPages) {
        try {
            if (response == null || response.isEmpty()) return null;
            String jsonStr = response.trim();
            if (jsonStr.startsWith("```")) {
                jsonStr = jsonStr.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
            }
            JsonNode root = objectMapper.readTree(jsonStr);

            JsonNode questionsNode;
            boolean firstContinuation = false;
            boolean lastComplete = true;
            if (root.isObject() && root.has("questions")) {
                questionsNode = root.get("questions");
                firstContinuation = root.path("firstQuestionIsContinuation").asBoolean(false);
                lastComplete = root.path("lastQuestionIsComplete").asBoolean(true);
            } else if (root.isArray()) {
                questionsNode = root;
            } else {
                return null;
            }
            if (!questionsNode.isArray()) return null;

            List<AiRubric> questions = objectMapper.readValue(questionsNode.toString(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, AiRubric.class));
            return new ChunkParseResult(new ArrayList<>(chunkPages), questions, firstContinuation, lastComplete);
        } catch (Exception e) {
            log.error("[题目文件解析-视觉] 分片响应解析失败: {}", e.getMessage());
            return null;
        }
    }

    private static class ExtractedImage {
        int index;
        String url;
        int pageIndex;
        byte[] imageBytes;

        ExtractedImage(int index, String url, int pageIndex, byte[] imageBytes) {
            this.index = index;
            this.url = url;
            this.pageIndex = pageIndex;
            this.imageBytes = imageBytes;
        }
    }

    /**
     * 提取PDF嵌入图片为JPEG字节数组（不上传OSS），按页码去重排序后统一编号。
     */
    private List<ExtractedImage> extractImagesFromPdfToBytes(PDDocument document) {
        List<ExtractedImage> images = new ArrayList<>();
        Set<String> uploadedHashes = new HashSet<>();
        int pageIndex = 0;
        for (PDPage page : document.getPages()) {
            PDResources resources = page.getResources();
            if (resources == null) { pageIndex++; continue; }

            for (COSName name : resources.getXObjectNames()) {
                try {
                    PDXObject xobject = resources.getXObject(name);
                    if (xobject instanceof PDImageXObject pdImage) {
                        BufferedImage bufferedImage = pdImage.getImage();
                        if (bufferedImage.getWidth() < 50 || bufferedImage.getHeight() < 50) continue;

                        byte[] imageBytes = writeJpeg(scaleDownToMaxEdge(bufferedImage, 1600), 0.8f);
                        String hash = DigestUtils.md5Hex(imageBytes);
                        if (uploadedHashes.contains(hash)) continue;
                        uploadedHashes.add(hash);

                        // 暂不上传，只保存字节；index 临时用-1，排序后重新编号
                        images.add(new ExtractedImage(-1, null, pageIndex, imageBytes));
                    }
                } catch (Exception e) {
                    log.debug("[PDF图片提取] 跳过无法处理的图片: {}", e.getMessage());
                }
            }
            pageIndex++;
        }

        // 按页码排序，同页内保持PDFBox遍历顺序
        images.sort(Comparator.comparingInt(a -> a.pageIndex));

        // 重新编号
        for (int i = 0; i < images.size(); i++) {
            images.get(i).index = i;
        }

        return images;
    }

    /**
     * 并行上传页面图片和嵌入图片到 OSS。
     * 单张上传失败不中断整体流程：页面图片失败的页不出现在结果Map中，嵌入图片 url 保持 null（后续映射时跳过）。
     * @return 页码 → 页面图片URL（按页码排序，仅含上传成功的页）
     */
    private Map<Integer, String> uploadAllImagesParallel(List<PageImage> pageImages,
                                                         List<ExtractedImage> extractedImages,
                                                         Long userId) {
        // 页面图片并行上传
        List<CompletableFuture<Map.Entry<Integer, String>>> pageFutures = new ArrayList<>();
        for (PageImage pi : pageImages) {
            String objectKey = "question-image/" + userId + "/page-" + pi.pageIndex + "-" + UUID.randomUUID() + ".jpg";
            pageFutures.add(CompletableFuture.supplyAsync(
                    () -> ossService.upload(objectKey, pi.imageBytes, "image/jpeg"), AiCommonSupport.AI_EXECUTOR)
                    .thenApply(url -> Map.entry(pi.pageIndex, url))
                    .exceptionally(ex -> {
                        log.warn("[OSS并行上传] 页面图片上传失败, pageIndex={}, error={}", pi.pageIndex, ex.getMessage());
                        return null;
                    }));
        }

        // 嵌入图片并行上传，回填 url
        List<CompletableFuture<Void>> extractFutures = new ArrayList<>();
        for (ExtractedImage img : extractedImages) {
            String objectKey = "question-image/" + userId + "/" + UUID.randomUUID() + ".jpg";
            extractFutures.add(CompletableFuture.runAsync(() -> {
                img.url = ossService.upload(objectKey, img.imageBytes, "image/jpeg");
            }, AiCommonSupport.AI_EXECUTOR).exceptionally(ex -> {
                log.warn("[OSS并行上传] 嵌入图片上传失败, index={}, error={}", img.index, ex.getMessage());
                return null;
            }));
        }

        // 等待全部完成
        CompletableFuture.allOf(pageFutures.toArray(new CompletableFuture[0])).join();
        CompletableFuture.allOf(extractFutures.toArray(new CompletableFuture[0])).join();

        // 收集页码 → URL（过滤失败的 null），按页码排序
        Map<Integer, String> urlByPageIndex = new TreeMap<>();
        for (CompletableFuture<Map.Entry<Integer, String>> f : pageFutures) {
            Map.Entry<Integer, String> entry = f.join();
            if (entry != null && entry.getValue() != null) {
                urlByPageIndex.put(entry.getKey(), entry.getValue());
            }
        }
        long extractOk = extractedImages.stream().filter(i -> i.url != null).count();
        log.info("[OSS并行上传] 完成, 页面图片成功 {}/{}, 嵌入图片成功 {}/{}",
                urlByPageIndex.size(), pageImages.size(), extractOk, extractedImages.size());
        return urlByPageIndex;
    }

    /**
     * 降级时清理已上传到 OSS 的页面图片和嵌入图片，避免资源孤立。
     * 入参允许为 null（异常发生在上传之前时）。
     */
    private void cleanupOssImages(List<String> pageImageUrls, List<ExtractedImage> extractedImages) {
        int deleted = 0;
        if (pageImageUrls != null) {
            for (String url : pageImageUrls) {
                String key = ossService.extractObjectKeyFromUrl(url);
                if (key != null) {
                    ossService.delete(key);
                    deleted++;
                }
            }
        }
        if (extractedImages != null) {
            for (ExtractedImage img : extractedImages) {
                if (img.url != null) {
                    String key = ossService.extractObjectKeyFromUrl(img.url);
                    if (key != null) {
                        ossService.delete(key);
                        deleted++;
                    }
                }
            }
        }
        if (deleted > 0) {
            log.info("[题目文件解析-视觉] 降级清理：已删除 {} 个OSS图片", deleted);
        }
    }

    private void mapImageReferencesToUrls(List<AiRubric> aiRubrics, List<ExtractedImage> extractedImages) {
        Map<Integer, String> indexToUrl = new HashMap<>();
        for (ExtractedImage img : extractedImages) {
            indexToUrl.put(img.index, img.url);
        }

        for (AiRubric rubric : aiRubrics) {
            if (rubric.getResources() == null) continue;
            for (AiRubric.AiResource resource : rubric.getResources()) {
                // 规范化 type 字段：AI 可能返回非标准的 type，统一为前端期望的值
                if (resource.getType() != null) {
                    String type = resource.getType().toLowerCase();
                    if ("image".equals(type) || "img".equals(type)) {
                        // 未分类的图片，根据 label 推断类型
                        if (resource.getLabel() != null && resource.getLabel().matches("^[A-Da-d]$")) {
                            resource.setType("option_image");
                            resource.setLabel(resource.getLabel().toUpperCase());
                        } else {
                            resource.setType("question_image");
                        }
                    } else {
                        // 确保已知类型的小写变体也能匹配，统一为标准命名
                        switch (type) {
                            case "question_image": resource.setType("question_image"); break;
                            case "option_image": resource.setType("option_image"); break;
                            case "answer_image": resource.setType("answer_image"); break;
                            case "explanation_image": resource.setType("explanation_image"); break;
                            default: resource.setType("question_image"); break;
                        }
                    }
                }

                if (resource.getUrl() != null && resource.getUrl().startsWith("图片")) {
                    try {
                        String numStr = resource.getUrl().replace("图片", "").trim();
                        int idx = Integer.parseInt(numStr);
                        String actualUrl = indexToUrl.get(idx);
                        if (actualUrl != null) {
                            resource.setUrl(actualUrl);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
    }
}
