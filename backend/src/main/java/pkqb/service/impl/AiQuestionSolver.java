package pkqb.service.impl;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import pkqb.common.Result;
import pkqb.config.ReactAgentFactory;
import pkqb.pojo.entity.ModelsEntity;
import pkqb.service.DashScopeRerankService;
import pkqb.service.UserApiKeyService;
import pkqb.util.LatexMathCleaner;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * AI 解题全链路（自 SpringAiAlibabaServiceImpl 拆分）：
 * 单类型生成（答案/解析/步骤）与"全部生成"（简单题单次调用、复杂题并行调用），
 * 含 RAG 上下文检索（向量召回 + 重排精排）、多模型 Agent 调用与结果编排。
 * 共享线程池 AI_EXECUTOR 与静态工具方法统一放在 {@link AiCommonSupport}，全局仅一份。
 */
@Component
@Slf4j
public class AiQuestionSolver {

    private final ObjectMapper objectMapper;
    private final ReactAgentFactory reactAgentFactory;
    private final UserApiKeyService userApiKeyService;
    private final VectorStore vectorStore;
    private final DashScopeRerankService rerankService;

    public AiQuestionSolver(ObjectMapper objectMapper,
                            ReactAgentFactory reactAgentFactory,
                            UserApiKeyService userApiKeyService,
                            VectorStore vectorStore,
                            DashScopeRerankService rerankService) {
        this.objectMapper = objectMapper;
        this.reactAgentFactory = reactAgentFactory;
        this.userApiKeyService = userApiKeyService;
        this.vectorStore = vectorStore;
        this.rerankService = rerankService;
    }

    Result<String> aiSolveQuestion(String questionText, String questionType, String optionsJson, String generateType, Long userId) {
        log.info("[AI解答] 开始生成，userId={}, questionType={}, generateType={}", userId, questionType, generateType);

        if (questionText == null || questionText.trim().isEmpty()) {
            return Result.error("题目内容不能为空");
        }

        if ("all".equals(generateType)) {
            return aiSolveAll(questionText, questionType, optionsJson, userId);
        }

        String prompt = buildAiSolvePromptWithRag(questionText, questionType, optionsJson, generateType, userId);
        if (prompt == null) {
            return Result.error("无效的生成类型");
        }

        try {
            Agent agent = reactAgentFactory.getMultiModelReactAgent(userId);
            String threadId = "ai_solve:" + userId + ":" + System.currentTimeMillis();
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(threadId)
                    .build();

            StringBuilder result = new StringBuilder();
            Optional<OverAllState> invoke = agent.invoke(prompt, config);

            if (invoke.isPresent()) {
                // 多模型并行模式（两个辅助模型）时输出各模型回答，否则输出最终答案
                result.append(buildAgentFinalOutput(invoke.get(), userId));
            }
            log.info("[AI解答] 生成完成，generateType={}", generateType);
            return Result.success(result.toString());
        } catch (Exception e) {
            log.error("[AI解答] 生成失败: ", e);
            if (AiCommonSupport.isApiKeyOrModelError(e)) {
                return Result.error("API Key或模型名称有误，请核验");
            }
            return Result.error("AI解答失败: " + e.getMessage());
        }
    }

    private Result<String> aiSolveAll(String questionText, String questionType, String optionsJson, Long userId) {
        try {
            String ragContext = searchVectorStore(questionText, userId);

            if (isSimpleQuestion(questionText, questionType)) {
                log.info("[AI解答] 简单问题，使用单次调用模式");
                return aiSolveAllInOne(questionText, questionType, optionsJson, ragContext, userId);
            } else {
                log.info("[AI解答] 复杂问题，使用并行调用模式");
                return aiSolveAllParallel(questionText, questionType, optionsJson, ragContext, userId);
            }
        } catch (Exception e) {
            log.error("[AI解答] 全部生成失败: ", e);
            if (AiCommonSupport.isApiKeyOrModelError(e)) {
                return Result.error("API Key或模型名称有误，请核验");
            }
            return Result.error("AI解答失败: " + e.getMessage());
        }
    }

    private boolean isSimpleQuestion(String questionText, String questionType) {
        if (questionText == null) return false;

        if ("multiple_choice".equals(questionType)) {
            return false;
        }

        if ("single_choice".equals(questionType) || "true_false".equals(questionType)) {
            return true;
        }

        if (questionText.length() < 50 && !"calculation".equals(questionType)) {
            return true;
        }

        return false;
    }

    private Result<String> aiSolveAllInOne(String questionText, String questionType, String optionsJson, String ragContext, Long userId) {
        try {
            String prompt = buildAllInOnePrompt(questionText, questionType, optionsJson, ragContext);

            Agent agent = reactAgentFactory.getMultiModelReactAgent(userId);
            String threadId = "ai_solve:" + userId + ":" + System.currentTimeMillis();
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(threadId)
                    .build();

            StringBuilder result = new StringBuilder();
            Optional<OverAllState> invoke = agent.invoke(prompt, config);
            if (invoke.isPresent()) {
                OverAllState state = invoke.get();
                state.value("final_answer").ifPresent(finalAnswer ->
                        result.append(extractMessageText(finalAnswer)));
            }

            String aiResponse = result.toString().trim();
            Map<String, String> results = parseAllInOneResponse(aiResponse, questionType);

            // 多模型并行模式（两个辅助模型）时，将各模型回答附加到解析中，并标注总结模型
            if (invoke.isPresent() && isDualAssistantMode(invoke.get())) {
                String[] names = getDualAssistantModelNames(userId);
                results.put("explanation", "【" + names[2] + " 的总结】\n"
                        + results.get("explanation") + "\n\n"
                        + buildAssistantAnswersText(names, invoke.get()));
            }

            results.replaceAll((k, v) -> LatexMathCleaner.clean(v));
            String jsonResult = objectMapper.writeValueAsString(results);
            log.info("[AI解答] 单次调用完成");
            return Result.success(jsonResult);
        } catch (Exception e) {
            log.error("[AI解答] 单次调用失败: ", e);
            throw new RuntimeException("AI调用失败: " + e.getMessage(), e);
        }
    }

    private String buildAllInOnePrompt(String questionText, String questionType, String optionsJson, String ragContext) {
        StringBuilder prompt = new StringBuilder();

        if (ragContext != null && !ragContext.isEmpty()) {
            prompt.append("以下是从知识库中检索到的相关内容，请参考这些内容来回答问题：\n\n");
            prompt.append(ragContext);
            prompt.append("\n\n---\n\n");
        }

        String typeDesc = getTypeDescription(questionType);
        prompt.append("请根据以下").append(typeDesc).append("，一次性生成答案和解析");
        if ("calculation".equals(questionType)) {
            prompt.append("以及计算步骤");
        }
        prompt.append("。\n\n");

        if (optionsJson != null && !optionsJson.isEmpty() && !optionsJson.equals("[]")) {
            prompt.append("选项：\n").append(optionsJson).append("\n\n");
        }

        prompt.append("题目：\n").append(questionText).append("\n\n");

        prompt.append("请严格按照以下JSON格式返回，不要包含任何其他内容：\n");
        prompt.append("{\n");

        if ("true_false".equals(questionType)) {
            prompt.append("  \"answer\": \"正确或错误\",\n");
        } else if ("single_choice".equals(questionType) || "multiple_choice".equals(questionType)) {
            prompt.append("  \"answer\": \"选项字母，如A或A,B,C\",\n");
        } else if ("short_answer".equals(questionType)) {
            prompt.append("  \"answer\": \"答案要点\",\n");
        } else if ("calculation".equals(questionType)) {
            prompt.append("  \"answer\": \"最终计算结果\",\n");
        }

        prompt.append("  \"explanation\": \"题目解析，150字以内\"\n");

        if ("calculation".equals(questionType)) {
            prompt.append(",\n  \"steps\": \"计算步骤，每步用换行分隔\"");
        }

        prompt.append("\n}\n\n");
        prompt.append("注意：只返回JSON，不要有任何其他文字。");

        return prompt.toString();
    }

    private Map<String, String> parseAllInOneResponse(String aiResponse, String questionType) {
        Map<String, String> results = new HashMap<>();
        results.put("answer", "");
        results.put("explanation", "");
        results.put("steps", "");

        if (aiResponse == null || aiResponse.isEmpty()) {
            log.warn("[AI解答] AI返回为空");
            return results;
        }

        String jsonStr = aiResponse;
        if (jsonStr.contains("```")) {
            jsonStr = jsonStr.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        }

        int start = jsonStr.indexOf("{");
        int end = jsonStr.lastIndexOf("}");
        if (start >= 0 && end > start) {
            jsonStr = jsonStr.substring(start, end + 1);
        }

        try {
            JsonNode node = objectMapper.readTree(jsonStr);

            if (node.has("answer")) {
                results.put("answer", node.get("answer").asText().trim());
            }
            if (node.has("explanation")) {
                results.put("explanation", node.get("explanation").asText().trim());
            }
            if (node.has("steps")) {
                results.put("steps", node.get("steps").asText().trim());
            }

            if (results.get("answer").isEmpty() && results.get("explanation").isEmpty()) {
                log.warn("[AI解答] JSON解析后内容为空，原始响应: {}", aiResponse.substring(0, Math.min(100, aiResponse.length())));
            }
        } catch (Exception e) {
            log.warn("[AI解答] JSON解析失败，尝试提取内容: {}, 原始响应前100字符: {}",
                    e.getMessage(), aiResponse.substring(0, Math.min(100, aiResponse.length())));

            if (aiResponse.contains("答案") || aiResponse.contains("正确") || aiResponse.contains("错误")) {
                results.put("answer", aiResponse);
            } else {
                results.put("answer", aiResponse);
                results.put("explanation", "AI返回格式异常，请重新生成");
            }
        }

        return results;
    }

    private Result<String> aiSolveAllParallel(String questionText, String questionType, String optionsJson, String ragContext, Long userId) {
        try {
            CompletableFuture<String[]> answerFuture = CompletableFuture.supplyAsync(() -> {
                String prompt = buildAiSolvePromptWithRagContext(questionText, questionType, optionsJson, "answer", ragContext);
                if (prompt == null) return new String[]{"", ""};
                return callMultiModelReactAgent(prompt, userId);
            }, AiCommonSupport.AI_EXECUTOR);

            CompletableFuture<String[]> explanationFuture = CompletableFuture.supplyAsync(() -> {
                String prompt = buildAiSolvePromptWithRagContext(questionText, questionType, optionsJson, "explanation", ragContext);
                if (prompt == null) return new String[]{"", ""};
                return callMultiModelReactAgent(prompt, userId);
            }, AiCommonSupport.AI_EXECUTOR);

            CompletableFuture<String[]> stepsFuture = CompletableFuture.completedFuture(new String[]{"", ""});
            if ("calculation".equals(questionType)) {
                stepsFuture = CompletableFuture.supplyAsync(() -> {
                    String prompt = buildAiSolvePromptWithRagContext(questionText, questionType, optionsJson, "steps", ragContext);
                    if (prompt == null) return new String[]{"", ""};
                    return callMultiModelReactAgent(prompt, userId);
                }, AiCommonSupport.AI_EXECUTOR);
            }

            try {
                CompletableFuture.allOf(answerFuture, explanationFuture, stepsFuture).get(120, java.util.concurrent.TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                log.error("[AI解题] 并行解题超时", e);
                throw new RuntimeException("AI解题超时，请稍后重试");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("AI解题被中断");
            } catch (ExecutionException e) {
                log.error("[AI解题] 并行解题执行异常", e);
                throw new RuntimeException("AI解题执行失败: " + e.getCause().getMessage());
            }

            Map<String, String> results = new HashMap<>();
            results.put("answer", answerFuture.get()[1]);
            results.put("explanation", explanationFuture.get()[1]);
            results.put("steps", stepsFuture.get()[1]);

            // 多模型并行模式（两个辅助模型）时，仅将辅助模型的回答附加到解析字段，避免答案/步骤字段重复冗余
            String assistantText = explanationFuture.get()[0];
            if (!assistantText.isEmpty()) {
                String[] names = getDualAssistantModelNames(userId);
                results.put("explanation", "【" + names[2] + " 的总结】\n"
                        + results.get("explanation") + "\n\n" + assistantText);
            }

            results.replaceAll((k, v) -> LatexMathCleaner.clean(v));
            String jsonResult = objectMapper.writeValueAsString(results);
            log.info("[AI解答] 并行调用完成");
            return Result.success(jsonResult);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            log.error("[AI解答] 并行调用执行异常: ", cause);
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException("AI调用失败: " + (cause != null ? cause.getMessage() : e.getMessage()), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[AI解答] 并行调用被中断: ", e);
            throw new RuntimeException("AI调用被中断: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("[AI解答] 并行调用失败: ", e);
            throw new RuntimeException("AI调用失败: " + e.getMessage(), e);
        }
    }

    private String buildAiSolvePromptWithRagContext(String questionText, String questionType, String optionsJson, String generateType, String ragContext) {
        StringBuilder prompt = new StringBuilder();

        if (ragContext != null && !ragContext.isEmpty()) {
            prompt.append("以下是从知识库中检索到的相关内容，请参考这些内容来回答问题：\n\n");
            prompt.append(ragContext);
            prompt.append("\n\n---\n\n");
        }

        prompt.append("请根据以下题目内容");

        String typeDesc = getTypeDescription(questionType);
        prompt.append("（").append(typeDesc).append("）");

        if (optionsJson != null && !optionsJson.isEmpty() && !optionsJson.equals("[]")) {
            prompt.append("\n\n选项：\n").append(optionsJson);
        }

        prompt.append("\n\n题目：\n").append(questionText);

        switch (generateType) {
            case "answer":
                prompt.append("\n\n请直接给出正确答案，不要包含任何解释。");
                if ("true_false".equals(questionType)) {
                    prompt.append(" 判断题请回答:正确或错误");
                } else if ("short_answer".equals(questionType)) {
                    prompt.append(" 简答题请给出简洁的答案要点。");
                } else if ("calculation".equals(questionType)) {
                    prompt.append(" 计算题请给出最终计算结果。");
                } else {
                    prompt.append(" 选择题请直接给出选项字母（如A、B、C、D，多选用逗号分隔）。");
                }
                break;
            case "explanation":
                prompt.append("\n\n请给出这道题的详细解析，字数控制在150字以内，要求简洁明了，突出重点。");
                break;
            case "steps":
                if (!"calculation".equals(questionType)) {
                    return null;
                }
                prompt.append("\n\n请给出这道计算题的详细计算步骤，每步一行，格式如下：\n");
                prompt.append("步骤1: xxx\n步骤2: xxx\n...");
                break;
            default:
                return null;
        }

        return prompt.toString();
    }

    /**
     * 判断是否为多模型并行模式（用户配置了两个辅助模型，两个辅助Agent都产生了回答）
     */
    private boolean isDualAssistantMode(OverAllState state) {
        return state.value("assistant_1_response").isPresent()
                && state.value("assistant_2_response").isPresent();
    }

    /**
     * 提取Agent输出的文本（AssistantMessage或其他类型）
     */
    private String extractMessageText(Object value) {
        if (value instanceof AssistantMessage) {
            return ((AssistantMessage) value).getText();
        }
        return value != null ? value.toString() : "";
    }

    /**
     * 获取多模型名称：[辅助模型1, 辅助模型2, 主模型]，取不到配置时使用默认名
     */
    private String[] getDualAssistantModelNames(Long userId) {
        List<ModelsEntity> assistantModels = userApiKeyService.getAssistantModels(userId);
        ModelsEntity mainModel = userApiKeyService.getMainModel(userId);
        String name1 = (assistantModels != null && assistantModels.size() > 0 && assistantModels.get(0) != null)
                ? assistantModels.get(0).getModelName() : "辅助模型1";
        String name2 = (assistantModels != null && assistantModels.size() > 1 && assistantModels.get(1) != null)
                ? assistantModels.get(1).getModelName() : "辅助模型2";
        String mainName = (mainModel != null) ? mainModel.getModelName() : "主模型";
        return new String[]{name1, name2, mainName};
    }

    /**
     * 构建多模型Agent的最终输出：
     * 多模型并行模式（两个辅助模型）时，输出两个辅助模型的回答和主模型的总结，并标注模型名称；
     * 其他模式仅返回最终答案。
     */
    private String buildAgentFinalOutput(OverAllState state, Long userId) {
        String finalAnswer = state.value("final_answer")
                .map(this::extractMessageText).orElse("");
        if (!isDualAssistantMode(state)) {
            return finalAnswer;
        }
        String[] names = getDualAssistantModelNames(userId);
        return buildAssistantAnswersText(names, state)
                + "\n\n【" + names[2] + " 的总结】\n"
                + finalAnswer;
    }

    /**
     * 构建带模型名标注的两个辅助模型回答文本（仅在多模型并行模式下调用）
     */
    private String buildAssistantAnswersText(String[] names, OverAllState state) {
        return "【" + names[0] + " 的回答】\n"
                + extractMessageText(state.value("assistant_1_response").get())
                + "\n\n【" + names[1] + " 的回答】\n"
                + extractMessageText(state.value("assistant_2_response").get());
    }

    /**
     * 调用多模型Agent（使用Multi-agent模式），返回 [辅助模型回答文本, 最终答案]。
     * 辅助模型回答文本仅在多模型并行模式（两个辅助模型）时非空。
     */
    private String[] callMultiModelReactAgent(String prompt, Long userId) {
        try {
            Agent agent = reactAgentFactory.getMultiModelReactAgent(userId);
            String threadId = "ai_solve:" + userId + ":" + System.currentTimeMillis();
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(threadId)
                    .build();

            String assistantText = "";
            String finalAnswer = "";
            Optional<OverAllState> invoke = agent.invoke(prompt, config);
            if (invoke.isPresent()) {
                OverAllState state = invoke.get();
                finalAnswer = state.value("final_answer")
                        .map(this::extractMessageText).orElse("");
                if (isDualAssistantMode(state)) {
                    String[] names = getDualAssistantModelNames(userId);
                    assistantText = buildAssistantAnswersText(names, state);
                }
            }
            return new String[]{assistantText, finalAnswer};
        } catch (Exception e) {
            log.error("[ReactAgent文本调用] 调用失败: ", e);
            throw new RuntimeException("AI调用失败: " + e.getMessage(), e);
        }
    }

    private String buildAiSolvePromptWithRag(String questionText, String questionType, String optionsJson, String generateType, Long userId) {
        StringBuilder prompt = new StringBuilder();

        String ragContext = searchVectorStore(questionText, userId);
        if (ragContext != null && !ragContext.isEmpty()) {
            prompt.append("以下是从知识库中检索到的相关内容，请参考这些内容来回答问题：\n\n");
            prompt.append(ragContext);
            prompt.append("\n\n---\n\n");
        }

        prompt.append("请根据以下题目内容");

        String typeDesc = getTypeDescription(questionType);
        prompt.append("（").append(typeDesc).append("）");

        if (optionsJson != null && !optionsJson.isEmpty() && !optionsJson.equals("[]")) {
            prompt.append("\n\n选项：\n").append(optionsJson);
        }

        prompt.append("\n\n题目：\n").append(questionText);

        switch (generateType) {
            case "answer":
                prompt.append("\n\n请直接给出正确答案，不要包含任何解释。");
                if ("true_false".equals(questionType)) {
                    prompt.append(" 判断题请回答:正确或错误");
                } else if ("short_answer".equals(questionType)) {
                    prompt.append(" 简答题请给出简洁的答案要点。");
                } else if ("calculation".equals(questionType)) {
                    prompt.append(" 计算题请给出最终计算结果。");
                } else {
                    prompt.append(" 选择题请直接给出选项字母（如A、B、C、D，多选用逗号分隔）。");
                }
                break;
            case "explanation":
                prompt.append("\n\n请给出这道题的详细解析，字数控制在150字以内，要求简洁明了，突出重点。");
                break;
            case "steps":
                if (!"calculation".equals(questionType)) {
                    return null;
                }
                prompt.append("\n\n请给出这道计算题的详细计算步骤，每步一行，格式如下：\n");
                prompt.append("步骤1: xxx\n步骤2: xxx\n...");
                break;
            default:
                return null;
        }

        return prompt.toString();
    }

    private String searchVectorStore(String query, Long userId) {
        try {
            // 粗排：扩大召回，为精排提供更多候选
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(20)
                    .similarityThreshold(0.5)
                    .build();

            List<Document> documents = vectorStore.similaritySearch(searchRequest);

            if (documents == null || documents.isEmpty()) {
                log.info("[AI解答-向量检索] 未找到相关文档");
                return null;
            }

            log.info("[AI解答-向量检索] 粗排召回 {} 个候选文档，开始精排", documents.size());

            // 精排：使用 qwen3-rerank 重排序，取 Top5
            List<Document> rerankedDocs = rerankService.rerank(query, documents, 5);

            String result = rerankedDocs.stream()
                    .map(doc -> {
                        String content = doc.getText();
                        String source = doc.getMetadata() != null ?
                                (String) doc.getMetadata().getOrDefault("source", "未知来源") : "未知来源";
                        return "【来源: " + source + "】\n" + content;
                    })
                    .collect(Collectors.joining("\n\n---\n\n"));

            log.info("[AI解答-向量检索] 精排完成，保留 {} 个文档片段", rerankedDocs.size());
            return result;
        } catch (Exception e) {
            log.warn("[AI解答-向量检索] 检索失败: {}", e.getMessage());
            return null;
        }
    }

    private String getTypeDescription(String questionType) {
        if (questionType == null) return "题目";
        switch (questionType) {
            case "single_choice": return "单选题";
            case "multiple_choice": return "多选题";
            case "true_false": return "判断题";
            case "short_answer": return "简答题";
            case "calculation": return "计算题";
            default: return questionType;
        }
    }
}
