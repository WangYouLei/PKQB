package pkqb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import pkqb.enums.ApiKeyMode;
import pkqb.service.UserApiKeyService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChatClient 工厂类
 * 决定使用系统默认 Bean 还是用户级 ChatClient
 */
@Component
@Slf4j
public class ChatClientFactory {

    private final ChatClient defaultChatClient;
    private final ChatClient chatClient;
    private final ChatClient milvusChatClient;
    private final ChatClientUserConfig chatClientUserConfig;
    private final UserApiKeyService userApiKeyService;

    private final Map<Long, ChatClient> userChatClientCache = new ConcurrentHashMap<>();

    public ChatClientFactory(
            @Qualifier("defaultChatClient") ChatClient defaultChatClient,
            @Qualifier("chatClient") ChatClient chatClient,
            @Qualifier("milvusChatClient") ChatClient milvusChatClient,
            ChatClientUserConfig chatClientUserConfig,
            UserApiKeyService userApiKeyService) {
        this.defaultChatClient = defaultChatClient;
        this.chatClient = chatClient;
        this.milvusChatClient = milvusChatClient;
        this.chatClientUserConfig = chatClientUserConfig;
        this.userApiKeyService = userApiKeyService;
    }

    /**
     * 获取默认 ChatClient
     */
    public ChatClient getDefaultChatClient(Long userId) {
        ApiKeyMode mode = userApiKeyService.getApiKeyMode(userId);
        if (mode == ApiKeyMode.LOCAL) {
            return defaultChatClient;
        }
        return getOrCreateUserDefaultChatClient(userId);
    }

    /**
     * 获取 ChatClient（AI学习助手）
     */
    public ChatClient getChatClient(Long userId) {
        ApiKeyMode mode = userApiKeyService.getApiKeyMode(userId);
        if (mode == ApiKeyMode.LOCAL) {
            return chatClient;
        }
        return getOrCreateUserChatClient(userId);
    }

    /**
     * 获取 Milvus ChatClient（知识库问答）
     */
    public ChatClient getMilvusChatClient(Long userId) {
        ApiKeyMode mode = userApiKeyService.getApiKeyMode(userId);
        if (mode == ApiKeyMode.LOCAL) {
            return milvusChatClient;
        }
        return getOrCreateUserMilvusChatClient(userId);
    }

    private ChatClient getOrCreateUserDefaultChatClient(Long userId) {
        return userChatClientCache.computeIfAbsent(userId, id -> {
            String apiKey = userApiKeyService.getPlainApiKey(id);
            String model = userApiKeyService.getModel(id);
            if (apiKey == null) {
                log.warn("用户 {} 没有个人 API Key，使用默认 ChatClient", id);
                return defaultChatClient;
            }
            return chatClientUserConfig.createUserDefaultChatClient(apiKey, model);
        });
    }

    private ChatClient getOrCreateUserChatClient(Long userId) {
        return userChatClientCache.computeIfAbsent(userId, id -> {
            String apiKey = userApiKeyService.getPlainApiKey(id);
            String model = userApiKeyService.getModel(id);
            if (apiKey == null) {
                log.warn("用户 {} 没有个人 API Key，使用默认 ChatClient", id);
                return chatClient;
            }
            return chatClientUserConfig.createUserChatClient(apiKey, model);
        });
    }

    private ChatClient getOrCreateUserMilvusChatClient(Long userId) {
        return userChatClientCache.computeIfAbsent(userId, id -> {
            String apiKey = userApiKeyService.getPlainApiKey(id);
            String model = userApiKeyService.getModel(id);
            if (apiKey == null) {
                log.warn("用户 {} 没有个人 API Key，使用默认 MilvusChatClient", id);
                return milvusChatClient;
            }
            return chatClientUserConfig.createUserMilvusChatClient(apiKey, model);
        });
    }

    /**
     * 清除用户缓存
     */
    public void clearUserCache(Long userId) {
        userChatClientCache.remove(userId);
        log.info("清除用户 {} 的 ChatClient 缓存", userId);
    }
}
