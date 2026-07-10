package pkqb.config;

import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pkqb.enums.ApiKeyMode;
import pkqb.pojo.entity.ModelsEntity;
import pkqb.service.UserApiKeyService;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class ReactAgentFactory {

    private static final int MAX_CACHE_SIZE = 1000;

    private final ReactAgent chatReactAgent;
    private final ReactAgent ragReactAgent;
    private final ReactAgent simpleReactAgent;
    private final ReactAgentUserConfig reactAgentUserConfig;
    private final UserApiKeyService userApiKeyService;
    private final DashScopeModelFactory dashScopeModelFactory;

    private final Map<Long, ReactAgent> userChatAgentCache = new ConcurrentHashMap<>();
    private final Map<Long, ReactAgent> userRagAgentCache = new ConcurrentHashMap<>();
    private final Map<Long, ReactAgent> userSimpleAgentCache = new ConcurrentHashMap<>();
    private final Map<Long, Agent> userMultiModelAgentCache = new ConcurrentHashMap<>();
    private final Map<Long, ReactAgent> userRubricParseAgentCache = new ConcurrentHashMap<>();
    private volatile ReactAgent defaultRubricParseAgent;

    public ReactAgentFactory(
            ReactAgent chatReactAgent,
            ReactAgent ragReactAgent,
            ReactAgent simpleReactAgent,
            ReactAgentUserConfig reactAgentUserConfig,
            UserApiKeyService userApiKeyService,
            DashScopeModelFactory dashScopeModelFactory) {
        this.chatReactAgent = chatReactAgent;
        this.ragReactAgent = ragReactAgent;
        this.simpleReactAgent = simpleReactAgent;
        this.reactAgentUserConfig = reactAgentUserConfig;
        this.userApiKeyService = userApiKeyService;
        this.dashScopeModelFactory = dashScopeModelFactory;
    }

    public ReactAgent getChatReactAgent(Long userId) {
        ApiKeyMode mode = userApiKeyService.getApiKeyMode(userId);
        if (mode == ApiKeyMode.LOCAL) {
            return chatReactAgent;
        }
        return getOrCreateUserChatReactAgent(userId);
    }

    public ReactAgent getRagReactAgent(Long userId) {
        ApiKeyMode mode = userApiKeyService.getApiKeyMode(userId);
        if (mode == ApiKeyMode.LOCAL) {
            return ragReactAgent;
        }
        return getOrCreateUserRagReactAgent(userId);
    }

    public ReactAgent getSimpleReactAgent(Long userId) {
        ApiKeyMode mode = userApiKeyService.getApiKeyMode(userId);
        if (mode == ApiKeyMode.LOCAL) {
            return simpleReactAgent;
        }
        return getOrCreateUserSimpleReactAgent(userId);
    }

    /**
     * 获取多模型Agent（用于AI解答功能）
     * 如果用户配置了主模型和辅助模型，则使用多模型模式
     * 否则使用单模型模式
     * 
     * @param userId 用户ID
     * @return Agent
     */
    public Agent getMultiModelReactAgent(Long userId) {
        ApiKeyMode mode = userApiKeyService.getApiKeyMode(userId);
        if (mode == ApiKeyMode.LOCAL) {
            return ragReactAgent;
        }
        
        if (userApiKeyService.supportsMultiModel(userId)) {
            return getOrCreateMultiModelReactAgent(userId);
        }
        
        return getOrCreateUserRagReactAgent(userId);
    }

    /**
     * 检查用户是否支持多模型查询
     * @param userId 用户ID
     * @return 是否支持多模型查询
     */
    public boolean supportsMultiModel(Long userId) {
        return userApiKeyService.supportsMultiModel(userId);
    }

    public ReactAgent getRubricParseAgent(Long userId) {
        ApiKeyMode mode = userApiKeyService.getApiKeyMode(userId);
        if (mode == ApiKeyMode.LOCAL) {
            if (defaultRubricParseAgent == null) {
                synchronized (this) {
                    if (defaultRubricParseAgent == null) {
                        String defaultApiKey = dashScopeModelFactory.getDefaultApiKey();
                        defaultRubricParseAgent = reactAgentUserConfig.createRubricParseAgent(defaultApiKey, null);
                    }
                }
            }
            return defaultRubricParseAgent;
        }
        return getOrCreateUserRubricParseAgent(userId);
    }

    private ReactAgent getOrCreateUserChatReactAgent(Long userId) {
        evictIfNeeded(userChatAgentCache);
        return userChatAgentCache.computeIfAbsent(userId, id -> {
            String apiKey = userApiKeyService.getPlainApiKey(id);
            ModelsEntity mainModel = userApiKeyService.getMainModel(id);
            String modelName = mainModel != null ? mainModel.getModelName() : null;
            
            if (apiKey == null) {
                log.warn("[ReactAgent工厂] 用户 {} 没有个人 API Key，使用默认 ChatReactAgent", id);
                return chatReactAgent;
            }
            return reactAgentUserConfig.createUserChatReactAgent(apiKey, modelName);
        });
    }

    private ReactAgent getOrCreateUserRagReactAgent(Long userId) {
        evictIfNeeded(userRagAgentCache);
        return userRagAgentCache.computeIfAbsent(userId, id -> {
            String apiKey = userApiKeyService.getPlainApiKey(id);
            ModelsEntity mainModel = userApiKeyService.getMainModel(id);
            String modelName = mainModel != null ? mainModel.getModelName() : null;
            
            if (apiKey == null) {
                log.warn("[ReactAgent工厂] 用户 {} 没有个人 API Key，使用默认 RagReactAgent", id);
                return ragReactAgent;
            }
            return reactAgentUserConfig.createUserRagReactAgent(apiKey, modelName);
        });
    }

    private ReactAgent getOrCreateUserSimpleReactAgent(Long userId) {
        evictIfNeeded(userSimpleAgentCache);
        return userSimpleAgentCache.computeIfAbsent(userId, id -> {
            String apiKey = userApiKeyService.getPlainApiKey(id);
            ModelsEntity mainModel = userApiKeyService.getMainModel(id);
            String modelName = mainModel != null ? mainModel.getModelName() : null;
            
            if (apiKey == null) {
                log.warn("[ReactAgent工厂] 用户 {} 没有个人 API Key，使用默认 SimpleReactAgent", id);
                return simpleReactAgent;
            }
            return reactAgentUserConfig.createUserSimpleReactAgent(apiKey, modelName);
        });
    }

    /**
     * 创建或获取多模型Agent
     * 使用Multi-agent模式：ParallelAgent让辅助模型并行回答，SequentialAgent整合结果
     */
    private Agent getOrCreateMultiModelReactAgent(Long userId) {
        evictIfNeeded(userMultiModelAgentCache);
        return userMultiModelAgentCache.computeIfAbsent(userId, id -> {
            String apiKey = userApiKeyService.getPlainApiKey(id);
            if (apiKey == null) {
                log.warn("[ReactAgent工厂] 用户 {} 没有个人 API Key，使用默认 RagReactAgent", id);
                return ragReactAgent;
            }
            
            ModelsEntity mainModel = userApiKeyService.getMainModel(id);
            if (mainModel == null) {
                log.warn("[ReactAgent工厂] 用户 {} 没有主模型，使用默认 RagReactAgent", id);
                return ragReactAgent;
            }
            
            List<ModelsEntity> assistantModels = userApiKeyService.getAssistantModels(id);
            if (assistantModels.isEmpty()) {
                log.info("[ReactAgent工厂] 用户 {} 没有辅助模型，使用单模型模式", id);
                return reactAgentUserConfig.createUserRagReactAgent(apiKey, mainModel.getModelName());
            }
            
            log.info("[ReactAgent工厂] 用户 {} 使用多模型模式，主模型: {}, 辅助模型数量: {}", 
                    id, mainModel.getModelName(), assistantModels.size());
            return reactAgentUserConfig.createMultiModelReactAgent(apiKey, mainModel, assistantModels);
        });
    }

    public void clearUserCache(Long userId) {
        userChatAgentCache.remove(userId);
        userRagAgentCache.remove(userId);
        userSimpleAgentCache.remove(userId);
        userMultiModelAgentCache.remove(userId);
        userRubricParseAgentCache.remove(userId);
        log.info("[ReactAgent工厂] 清除用户 {} 的 ReactAgent 缓存", userId);
    }

    private void evictIfNeeded(Map<Long, ?> cache) {
        if (cache.size() >= MAX_CACHE_SIZE) {
            log.warn("[ReactAgent工厂] 缓存已达上限({})，清除一半", cache.size());
            Iterator<Long> it = cache.keySet().iterator();
            int toRemove = MAX_CACHE_SIZE / 2;
            int removed = 0;
            while (it.hasNext() && removed < toRemove) {
                it.next();
                it.remove();
                removed++;
            }
        }
    }

    private ReactAgent getOrCreateUserRubricParseAgent(Long userId) {
        evictIfNeeded(userRubricParseAgentCache);
        return userRubricParseAgentCache.computeIfAbsent(userId, id -> {
            String apiKey = userApiKeyService.getPlainApiKey(id);
            ModelsEntity mainModel = userApiKeyService.getMainModel(id);
            String modelName = mainModel != null ? mainModel.getModelName() : null;
            ModelsEntity visionModel = userApiKeyService.getVisionModel(id);
            String visionModelName = visionModel != null ? visionModel.getModelName() : null;

            if (apiKey == null) {
                log.warn("[ReactAgent工厂] 用户 {} 没有个人 API Key，使用默认 apiKey 创建 RubricParseAgent", id);
                apiKey = dashScopeModelFactory.getDefaultApiKey();
            }
            return reactAgentUserConfig.createRubricParseAgent(apiKey, modelName, visionModelName);
        });
    }
}
