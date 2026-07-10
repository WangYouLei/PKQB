package pkqb.config;

import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.ParallelAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.summarization.SummarizationHook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import pkqb.pojo.entity.ModelsEntity;
import pkqb.service.DashScopeRerankService;
import pkqb.service.MinioService;
import pkqb.tool.ImageViewTool;

import java.util.List;

@Component
@Slf4j
public class ReactAgentUserConfig {

    private static final int MODEL_CALL_LIMIT = 3;

    private static final String ASSISTANT_AGENT_INSTRUCTION = """
            你是一个专业的AI助手。请根据用户的问题给出准确、详细的回答。
            
            要求：
            1. 直接回答问题，不要输出任何开场白或结束语
            2. 回答要准确、专业
            3. 如果问题涉及知识，请给出详细的解释
            4. 如果问题需要推理，请给出清晰的推理过程
            5. 回答要简洁明了，突出重点
            
            用户问题：{input}
            """;

    private static final String SUMMARY_AGENT_INSTRUCTION = """
            你是一个专业的答案整合助手。这是你的唯一任务：
                            1. 仔细阅读两个辅助模型的回答。
                            2. 提炼出一个最优答案。
                            3. **输出规则：直接输出整合后的最终答案。绝对不要输出任何分析过程、解释、前缀或后缀。**
                            4. **禁止提及"模型1"、"模型2"、"整合"、"根据..."等词汇。**
                            5. **你的输出必须是纯粹的答案文本，不能有任何额外的包装。**
                            6. 如果两个模型的回答一致，直接输出该答案。
                            7. 如果有差异，选择更合理、更详细的解释。
            
            辅助模型1的回答：{assistant_1_response}
            
            辅助模型2的回答：{assistant_2_response}
            
            请直接输出最终答案：
            """;

    private static final String SINGLE_ASSISTANT_SUMMARY_INSTRUCTION = """
            你是一个专业的答案优化助手。你需要对辅助模型的回答进行审核和优化。
            
            重要要求：
            1. 直接输出优化后的最终答案，不要输出任何分析过程
            2. 不要提及"优化"、"审核"等词汇
            3. 只输出给用户的最终答案内容
            
            优化原则：
            - 确保回答准确、完整
            - 优化回答的结构和逻辑
            - 保持答案简洁明了，突出重点
            
            辅助模型的回答：{assistant_1_response}
            
            请直接输出最终答案：
            """;

    private final DashScopeModelFactory modelFactory;
    private final MemorySaver memorySaver;
    private final VectorStore vectorStore;
    private final MinioService minioService;
    private final DashScopeRerankService rerankService;

    public ReactAgentUserConfig(DashScopeModelFactory modelFactory, MemorySaver memorySaver,
                                VectorStore vectorStore, MinioService minioService,
                                DashScopeRerankService rerankService) {
        this.modelFactory = modelFactory;
        this.memorySaver = memorySaver;
        this.vectorStore = vectorStore;
        this.minioService = minioService;
        this.rerankService = rerankService;
    }

    public ReactAgent createUserChatReactAgent(String apiKey) {
        return createUserChatReactAgent(apiKey, null);
    }

    public ReactAgent createUserChatReactAgent(String apiKey, String model) {
        String actualModel = (model != null && !model.isEmpty()) ? model : modelFactory.getDefaultModel();
        log.info("[ReactAgent用户配置] 为用户创建个人 ChatReactAgent，模型: {}", actualModel);
        ChatModel chatModel = modelFactory.createChatModel(apiKey, actualModel);
        SummarizationHook userHook = createUserSummarizationHook(chatModel);
        ModelCallLimitHook limitHook = createModelCallLimitHook();
        return ReactAgent.builder()
                .name("chat_agent_user")
                .model(chatModel)
                .systemPrompt(AiConstants.CHAT_SYSTEM_PROMPT)
                .hooks(userHook, limitHook)
                .saver(memorySaver)
                .build();
    }

    public ReactAgent createUserRagReactAgent(String apiKey) {
        return createUserRagReactAgent(apiKey, null);
    }

    public ReactAgent createUserRagReactAgent(String apiKey, String model) {
        String actualModel = (model != null && !model.isEmpty()) ? model : modelFactory.getDefaultModel();
        log.info("[ReactAgent用户配置] 为用户创建个人 RagReactAgent，模型: {}", actualModel);
        ChatModel chatModel = modelFactory.createChatModel(apiKey, actualModel);
        SummarizationHook userHook = createUserSummarizationHook(chatModel);

        //ModelCallLimitHook limitHook = createModelCallLimitHook();
        ModelCallLimitHook limitHook = ModelCallLimitHook.builder().runLimit(1).build();
        RagAgentHook ragHook = new RagAgentHook(vectorStore, rerankService);
        return ReactAgent.builder()
                .name("rag_agent_user")
                .model(chatModel)
                .systemPrompt(AiConstants.RAG_SYSTEM_PROMPT)
                .tools()
                .hooks(ragHook, limitHook)
                .interceptors(new RAGContextInterceptor())
                .saver(memorySaver)
                .build();
    }

    public ReactAgent createUserSimpleReactAgent(String apiKey) {
        return createUserSimpleReactAgent(apiKey, null);
    }

    public ReactAgent createUserSimpleReactAgent(String apiKey, String model) {
        String actualModel = (model != null && !model.isEmpty()) ? model : modelFactory.getDefaultModel();
        log.info("[ReactAgent用户配置] 为用户创建个人 SimpleReactAgent，模型: {}", actualModel);
        ChatModel chatModel = modelFactory.createChatModel(apiKey, actualModel);
        return ReactAgent.builder()
                .name("simple_agent_user")
                .model(chatModel)
                .build();
    }

    /**
     * 创建多模型Agent（使用Multi-agent模式）
     * 使用ParallelAgent让两个辅助模型并行回答，然后由主模型整合结果
     * 
     * @param apiKey 用户API Key
     * @param mainModel 主模型（用于整合结果）
     * @param assistantModels 辅助模型列表（最多2个）
     * @return 多模型Agent
     */
    public Agent createMultiModelReactAgent(String apiKey, ModelsEntity mainModel, List<ModelsEntity> assistantModels) {
        log.info("[多模型Agent] 创建多模型Agent，主模型: {}, 辅助模型数量: {}", 
                mainModel.getModelName(), assistantModels.size());
        
        ChatModel mainChatModel = modelFactory.createChatModel(apiKey, mainModel.getModelName());
        SummarizationHook userHook = createUserSummarizationHook(mainChatModel);
        ModelCallLimitHook limitHook = createModelCallLimitHook();
        
        if (assistantModels == null || assistantModels.isEmpty()) {
            log.info("[多模型Agent] 没有辅助模型，创建单模型Agent");
            return ReactAgent.builder()
                    .name("single_model_agent")
                    .model(mainChatModel)
                    .systemPrompt(AiConstants.RAG_SYSTEM_PROMPT)
                    .hooks(userHook, limitHook)
                    .saver(memorySaver)
                    .build();
        }
        
        if (assistantModels.size() == 1) {
            log.info("[多模型Agent] 只有1个辅助模型，创建单辅助模型Agent");
            return createSingleAssistantAgent(apiKey, mainModel, assistantModels.get(0));
        }
        
        ReactAgent assistantAgent1 = createAssistantReactAgent(apiKey, assistantModels.get(0), 1);
        ReactAgent assistantAgent2 = createAssistantReactAgent(apiKey, assistantModels.get(1), 2);
        
        log.info("[多模型Agent] 创建并行Agent，辅助模型: {} 和 {}", 
                assistantModels.get(0).getModelName(), assistantModels.get(1).getModelName());
        
        ParallelAgent parallelAgent = ParallelAgent.builder()
                .name("parallel_assistant_agents")
                .description("两个辅助模型并行回答问题")
                .subAgents(List.of(assistantAgent1, assistantAgent2))
                .mergeOutputKey("parallel_results")
                .build();
        
        ReactAgent summaryAgent = ReactAgent.builder()
                .name("summary_agent")
                .model(mainChatModel)
                .instruction(SUMMARY_AGENT_INSTRUCTION)
                .outputKey("final_answer")
                .build();
        
        log.info("[多模型Agent] 创建顺序Agent，整合并行结果");
        
        return SequentialAgent.builder()
                .name("multi_model_sequential_agent")
                .description("多模型工作流：并行回答 -> 整合结果")
                .subAgents(List.of(parallelAgent, summaryAgent))
                .build();
    }

    /**
     * 创建单辅助模型Agent（一个主模型 + 一个辅助模型）
     */
    private Agent createSingleAssistantAgent(String apiKey, ModelsEntity mainModel, ModelsEntity assistantModel) {
        log.info("[多模型Agent] 创建单辅助模型Agent，主模型: {}, 辅助模型: {}", 
                mainModel.getModelName(), assistantModel.getModelName());
        
        ChatModel mainChatModel = modelFactory.createChatModel(apiKey, mainModel.getModelName());
        ChatModel assistantChatModel = modelFactory.createChatModel(apiKey, assistantModel.getModelName());
        
        ReactAgent assistantAgent = ReactAgent.builder()
                .name("assistant_model_1")
                .model(assistantChatModel)
                .instruction(ASSISTANT_AGENT_INSTRUCTION)
                .outputKey("assistant_1_response")
                .build();
        
        ReactAgent summaryAgent = ReactAgent.builder()
                .name("summary_agent")
                .model(mainChatModel)
                .instruction(SINGLE_ASSISTANT_SUMMARY_INSTRUCTION)
                .outputKey("final_answer")
                .build();
        
        return SequentialAgent.builder()
                .name("single_assistant_agent")
                .description("单辅助模型工作流：辅助模型回答 -> 主模型优化")
                .subAgents(List.of(assistantAgent, summaryAgent))
                .build();
    }

    /**
     * 创建辅助模型ReactAgent
     */
    private ReactAgent createAssistantReactAgent(String apiKey, ModelsEntity model, int index) {
        log.info("[多模型Agent] 创建辅助模型Agent {}: {}", index, model.getModelName());
        ChatModel chatModel = modelFactory.createChatModel(apiKey, model.getModelName());
        
        return ReactAgent.builder()
                .name("assistant_model_" + index)
                .model(chatModel)
                .instruction(ASSISTANT_AGENT_INSTRUCTION)
                .outputKey("assistant_" + index + "_response")
                .build();
    }

    private SummarizationHook createUserSummarizationHook(ChatModel chatModel) {
        return SummarizationHook.builder()
                .model(chatModel)
                .maxTokensBeforeSummary(4000)
                .messagesToKeep(20)
                .build();
    }

    private ModelCallLimitHook createModelCallLimitHook() {
        return ModelCallLimitHook.builder()
                .runLimit(MODEL_CALL_LIMIT)
                .build();
    }

    public ReactAgent createRubricParseAgent(String apiKey, String model) {
        return createRubricParseAgent(apiKey, model, null);
    }

    public ReactAgent createRubricParseAgent(String apiKey, String model, String visionModelName) {
        String actualModel = (model != null && !model.isEmpty()) ? model : modelFactory.getDefaultModel();
        log.info("[ReactAgent用户配置] 为用户创建 RubricParseAgent，模型: {}, 视觉模型: {}", actualModel, visionModelName);
        ChatModel chatModel = modelFactory.createChatModel(apiKey, actualModel);
        ChatModel visionChatModel = (visionModelName != null && !visionModelName.isEmpty())
                ? modelFactory.createVisionChatModel(apiKey, visionModelName)
                : modelFactory.createVisionChatModel();
        FunctionToolCallback imageViewTool = ImageViewTool.createTool(minioService, visionChatModel);
        ModelCallLimitHook limitHook = ModelCallLimitHook.builder().runLimit(5).build();

        return ReactAgent.builder()
                .name("rubric_parse_agent")
                .model(chatModel)
                .systemPrompt(AiConstants.RUBRIC_PARSE_SYSTEM_PROMPT)
                .tools(imageViewTool)
                .hooks(limitHook)
                .saver(memorySaver)
                .build();
    }
}
