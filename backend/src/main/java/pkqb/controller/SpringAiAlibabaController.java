package pkqb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pkqb.common.Result;
import pkqb.pojo.dto.AiRubric;
import pkqb.pojo.dto.QueryRequest;
import pkqb.service.RateLimitService;
import pkqb.service.SpringAiAlibabaService;
import reactor.core.publisher.Flux;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@Slf4j
@RequestMapping("/api/ai")
@Tag(name = "AI服务", description = "AI对话、知识库管理、题库解析接口")
public class SpringAiAlibabaController {

  private final SpringAiAlibabaService saaService;
  private final RateLimitService rateLimitService;

    public SpringAiAlibabaController(SpringAiAlibabaService saaService, RateLimitService rateLimitService) {
        this.saaService = saaService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping(value = "/add-documentsFile")
    @Operation(summary = "上传知识库文档", description = "上传文档到向量知识库")
    public Result<String> addDocumentsFile(
            @Parameter(description = "文档文件") @RequestParam("file") MultipartFile file,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        // 文件类型白名单
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return Result.error("文件名不能为空");
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        java.util.Set<String> allowedExtensions = java.util.Set.of(
            "pdf", "doc", "docx", "txt", "md", "ppt", "pptx", "xls", "xlsx"
        );
        if (!allowedExtensions.contains(extension)) {
            return Result.error("不支持的文件类型: " + extension);
        }
        return saaService.addDocuments(file, userId);
    }

    @PostMapping("/handle-rubricFile")
    @Operation(summary = "解析题目文件（AI）", description = "上传题目文件并使用AI解析为结构化题目数据")
    public Result<List<AiRubric>> handleRubricFile(
            @Parameter(description = "题目文件") @RequestParam("file") MultipartFile file,
            HttpServletRequest httpRequest,
            @Parameter(description = "模型类型：2=视觉模型，1=纯文本，不传则自动选择") @RequestParam(value = "modelType", required = false) Integer modelType) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        // 文件类型白名单
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return Result.error("文件名不能为空");
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        java.util.Set<String> allowedExtensions = java.util.Set.of(
            "pdf", "doc", "docx", "txt", "md", "ppt", "pptx", "xls", "xlsx"
        );
        if (!allowedExtensions.contains(extension)) {
            return Result.error("不支持的文件类型: " + extension);
        }
        return saaService.handleRubricFile(file, userId, modelType);
    }

    @PostMapping("/handle-rubricFile-local")
    @Operation(summary = "解析题目文件（本地）", description = "上传题目文件并使用本地算法解析为结构化题目数据")
    public Result<List<AiRubric>> handleRubricFileLocal(
            @Parameter(description = "题目文件") @RequestParam("file") MultipartFile file,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        // 文件类型白名单
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return Result.error("文件名不能为空");
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        java.util.Set<String> allowedExtensions = java.util.Set.of(
            "pdf", "doc", "docx", "txt", "md", "ppt", "pptx", "xls", "xlsx"
        );
        if (!allowedExtensions.contains(extension)) {
            return Result.error("不支持的文件类型: " + extension);
        }
        return saaService.handleRubricFileLocal(file, userId);
    }

    @PostMapping(value = "/query", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "AI对话", description = "普通AI对话（流式返回）")
    public Flux<String> query(
            @Valid @RequestBody QueryRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Flux.error(new RuntimeException("未登录或登录已过期"));
        }
        return saaService.query(request.getQuery(), request.getSessionId(), userId);
    }

    @PostMapping(value = "/rag-query", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "知识库问答", description = "基于RAG的向量知识库问答（流式返回）")
    public Flux<String> ragQuery(
            @Valid @RequestBody QueryRequest request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Flux.error(new RuntimeException("未登录或登录已过期"));
        }
        return saaService.ragQuery(request.getQuery(), request.getSessionId(), userId);
    }

    @GetMapping(value = "/get-historyList")
    @Operation(summary = "获取历史会话列表", description = "获取用户的历史会话列表")
    public Result<List<String>> getHistory(
            HttpServletRequest httpRequest,
            @Parameter(description = "会话类型：rag或chat") @RequestParam(value = "type") String type){
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        if (!"rag".equals(type) && !"chat".equals(type)) {
            return Result.error("type参数必须为rag或chat");
        }
        return saaService.getHistory(userId.toString(), type);
    }

    @GetMapping(value = "/get-history-by-sessionId")
    @Operation(summary = "获取会话历史详情", description = "获取指定会话的聊天记录")
    public Result<Object> getHistoryById(
            @Parameter(description = "会话ID") @RequestParam(value = "sessionId") String sessionId,
            HttpServletRequest httpRequest,
            @Parameter(description = "会话类型") @RequestParam(value = "type") String type){
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        if (!"rag".equals(type) && !"chat".equals(type)) {
            return Result.error("type参数必须为rag或chat");
        }
        return saaService.getHistoryBySessionId(sessionId, userId.toString(), type);
    }

    @DeleteMapping("/delete-history")
    @Operation(summary = "删除会话", description = "删除指定的聊天会话记录（AI对话或知识库问答）")
    public Result<Boolean> deleteHistory(
            @Parameter(description = "会话ID") @RequestParam String sessionId,
            HttpServletRequest httpRequest,
            @Parameter(description = "会话类型：rag或chat") @RequestParam String type) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        if (!"rag".equals(type) && !"chat".equals(type)) {
            return Result.error("type参数必须为rag或chat");
        }
        return saaService.deleteHistory(sessionId, userId, type);
    }

    @DeleteMapping("/delete-messages")
    @Operation(summary = "删除消息", description = "删除会话中的指定消息")
    public Result<Boolean> deleteMessages(
            @Parameter(description = "会话ID") @RequestParam String sessionId,
            HttpServletRequest httpRequest,
            @Parameter(description = "会话类型：rag或chat") @RequestParam String type,
            @Parameter(description = "消息索引列表") @RequestParam List<Integer> messageIndices) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        if (!"rag".equals(type) && !"chat".equals(type)) {
            return Result.error("type参数必须为rag或chat");
        }
        if (messageIndices != null && messageIndices.size() > 100) {
            return Result.error("删除消息数量不能超过100条");
        }
        return saaService.deleteMessages(sessionId, userId, type, messageIndices);
    }

    @GetMapping("/usage")
    @Operation(summary = "获取使用次数", description = "获取用户今日AI功能使用次数")
    public Result<Map<String, Object>> getUsage(
            HttpServletRequest httpRequest,
            @Parameter(description = "功能类型：chat或rag") @RequestParam String type) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        if (!"rag".equals(type) && !"chat".equals(type)) {
            return Result.error("type参数必须为rag或chat");
        }
        Map<String, Object> result = new HashMap<>();
        
        boolean shouldLimit = rateLimitService.shouldRateLimit(userId);
        result.put("hasOwnApiKey", !shouldLimit);
        
        if (shouldLimit) {
            long used = rateLimitService.getTodayUsage(userId, type);
            int limit = "chat".equals(type) ? 30 : 30;
            result.put("used", used);
            result.put("limit", limit);
            result.put("remaining", Math.max(0, limit - used));
        } else {
            result.put("used", 0);
            result.put("limit", -1);
            result.put("remaining", -1);
        }
        
        return Result.success(result);
    }

    @PostMapping("/ai-solve")
    @Operation(summary = "AI解答题目", description = "使用AI生成题目的答案、解析或计算步骤")
    public Result<String> aiSolveQuestion(
            @Parameter(description = "题目内容") @RequestParam("questionText") String questionText,
            @Parameter(description = "题目类型") @RequestParam("questionType") String questionType,
            @Parameter(description = "选项JSON") @RequestParam(value = "optionsJson", required = false) String optionsJson,
            @Parameter(description = "生成类型：answer/explanation/steps") @RequestParam("generateType") String generateType,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        if (questionText == null || questionText.isBlank()) {
            return Result.error("题目内容不能为空");
        }
        if (questionType == null || questionType.isBlank()) {
            return Result.error("题目类型不能为空");
        }
        if (generateType == null || generateType.isBlank()) {
            return Result.error("生成类型不能为空");
        }
        return saaService.aiSolveQuestion(questionText, questionType, optionsJson, generateType, userId);
    }
}
