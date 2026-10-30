package pkqb.service.impl;

import pkqb.pojo.dto.AiRubric;
import pkqb.util.LatexMathCleaner;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * AI 链路共享基础设施（包内可见，非 Spring 组件）：
 * 视觉解析（RubricVisionParser）、AI 解题（AiQuestionSolver）与主服务（SpringAiAlibabaServiceImpl）
 * 共用的线程池与静态工具方法。线程池等静态可变状态在本类中只存在一份，
 * 生命周期由 SpringAiAlibabaServiceImpl 容器销毁时统一关闭。
 */
final class AiCommonSupport {

    private AiCommonSupport() {
    }

    static final ExecutorService AI_EXECUTOR = new ThreadPoolExecutor(
            6, 6, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
            new java.util.concurrent.LinkedBlockingQueue<>(100),
            r -> {
                Thread t = new Thread(r, "ai-executor-" + System.currentTimeMillis());
                t.setDaemon(true);
                return t;
            },
            new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
    );

    /** 供 SpringAiAlibabaServiceImpl 在容器销毁时关闭共享线程池 */
    static void shutdownAiExecutor() {
        AI_EXECUTOR.shutdown();
        try {
            if (!AI_EXECUTOR.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
                AI_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            AI_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    static boolean isApiKeyOrModelError(Throwable e) {
        if (e == null) return false;
        String message = e.getMessage();
        if (message == null) {
            message = e.getClass().getSimpleName();
        }
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("invalid api")
                || lowerMessage.contains("invalid api-key")
                || lowerMessage.contains("invalid apikey")
                || lowerMessage.contains("invalid key")
                || lowerMessage.contains("api key") && lowerMessage.contains("invalid")
                || lowerMessage.contains("authentication")
                || lowerMessage.contains("unauthorized")
                || lowerMessage.contains("401")
                || lowerMessage.contains("403")
                || lowerMessage.contains("model") && lowerMessage.contains("not found")
                || lowerMessage.contains("model") && lowerMessage.contains("does not exist")
                || lowerMessage.contains("quota")
                || lowerMessage.contains("rate limit");
    }

    /**
     * 将AI返回题目中残留的LaTeX数学语法转换为Unicode符号，避免前端纯文本展示出现乱码
     */
    static void sanitizeRubricMath(List<AiRubric> rubrics) {
        if (rubrics == null) {
            return;
        }
        for (AiRubric rubric : rubrics) {
            if (rubric == null) continue;
            rubric.setQuestion(LatexMathCleaner.clean(rubric.getQuestion()));
            rubric.setAnswer(LatexMathCleaner.clean(rubric.getAnswer()));
            rubric.setExplanation(LatexMathCleaner.clean(rubric.getExplanation()));
            if (rubric.getOptions() != null) {
                for (int i = 0; i < rubric.getOptions().length; i++) {
                    rubric.getOptions()[i] = LatexMathCleaner.clean(rubric.getOptions()[i]);
                }
            }
            if (rubric.getCalculationSteps() != null) {
                rubric.setCalculationSteps(rubric.getCalculationSteps().stream()
                        .map(LatexMathCleaner::clean)
                        .collect(java.util.stream.Collectors.toList()));
            }
        }
    }
}
