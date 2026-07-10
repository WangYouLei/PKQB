package pkqb.config;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
class RAGContextInterceptor extends ModelInterceptor {
    private static final String RAG_CONTEXT_KEY = "rag_context";

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        // 从请求上下文中获取检索到的 RAG 上下文
        // RAG 上下文在 AgentHook 的 beforeAgent 中已经存储到状态中
        Map<String, Object> context = request.getContext();
        String ragContext = (String) context.get(RAG_CONTEXT_KEY);

        if (ragContext == null || ragContext.isEmpty()) {
            // 如果没有检索到上下文，直接调用处理器
            return handler.call(request);
        }

        // 增强 systemPrompt
        String enhancedSystemPrompt = String.format("""
                下面是从向量数据库中获取到的内容：
                %s
                """, ragContext);

        // 合并原有的 systemPrompt 和检索到的上下文
        SystemMessage enhancedSystemMessage;
        if (request.getSystemMessage() == null) {
            enhancedSystemMessage = new SystemMessage(enhancedSystemPrompt);
        } else {
            enhancedSystemMessage = new SystemMessage(
                    request.getSystemMessage().getText() + "\n" + enhancedSystemPrompt
            );
        }

        // 创建增强的请求
        ModelRequest enhancedRequest = ModelRequest.builder(request)
                .systemMessage(enhancedSystemMessage)
                .build();

        return handler.call(enhancedRequest);
    }

    @Override
    public String getName() {
        return "rag_context_interceptor";
    }
}
