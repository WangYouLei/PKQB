package pkqb.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import pkqb.config.ChatClientFactory;
import pkqb.service.ChatMemoryService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMemoryServiceImpl implements ChatMemoryService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ChatClientFactory chatClientFactory;
    private final ObjectMapper objectMapper;

    private static final String MEMORY_KEY_PREFIX = "spring_ai_alibaba_chat_memory:history:";
    private static final String LOCK_KEY_PREFIX = "chat_memory:compress:lock:";
    private static final String SUMMARY_PROMPT = """
            请将以下对话历史压缩成简洁的摘要，要求：
            1. 保留关键信息、重要结论和决策
            2. 保留用户的核心问题和需求
            3. 保留重要的上下文信息
            4. 使用简洁的语言，控制在200字以内
            5. 不要包含具体的对话细节，只保留要点
            
            对话历史：
            %s
            
            请直接输出摘要内容，不要有任何前缀或说明。
            """;

    @Override
    public void compressIfNeeded(String userId, String sessionId, String type) {
        String memoryKey = MEMORY_KEY_PREFIX + type + ":" + userId + ":" + sessionId;
        String lockKey = LOCK_KEY_PREFIX + type + ":" + userId + ":" + sessionId;
        
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", 30, TimeUnit.SECONDS);
        if (locked == null || !locked) {
            log.debug("[上下文压缩] 获取锁失败，跳过压缩，userId={}, sessionId={}", userId, sessionId);
            return;
        }
        
        try {
            Long messageCount = stringRedisTemplate.opsForList().size(memoryKey);
            if (messageCount == null || messageCount <= COMPRESS_THRESHOLD) {
                return;
            }
            
            log.info("[上下文压缩] 开始压缩，userId={}, sessionId={}, messageCount={}", 
                    userId, sessionId, messageCount);
            
            List<String> allMessages = stringRedisTemplate.opsForList().range(memoryKey, 0, -1);
            if (allMessages == null || allMessages.size() <= COMPRESS_THRESHOLD) {
                return;
            }
            
            int messagesToCompress = allMessages.size() - WINDOW_SIZE;
            if (messagesToCompress <= 0) {
                return;
            }
            List<String> messagesToKeep = new ArrayList<>();
            List<String> messagesForSummary = new ArrayList<>();
            
            for (int i = 0; i < allMessages.size(); i++) {
                if (i < messagesToCompress) {
                    messagesForSummary.add(allMessages.get(i));
                } else {
                    messagesToKeep.add(allMessages.get(i));
                }
            }
            
            String existingSummary = getSummary(userId, sessionId, type);
            StringBuilder contentForSummary = new StringBuilder();
            if (existingSummary != null && !existingSummary.isEmpty()) {
                contentForSummary.append("【之前的对话摘要】\n").append(existingSummary).append("\n\n");
            }
            contentForSummary.append("【本轮需要压缩的对话】\n");
            
            for (String msgJson : messagesForSummary) {
                try {
                    JsonNode node = objectMapper.readTree(msgJson);
                    String messageType = node.has("messageType") ? node.get("messageType").asText() : "";
                    String content = node.has("text") ? node.get("text").asText() : "";
                    String role;
                    if ("USER".equals(messageType)) {
                        role = "用户";
                    } else if ("SYSTEM".equals(messageType)) {
                        role = "系统";
                    } else {
                        role = "AI";
                    }
                    contentForSummary.append(role).append(": ").append(content).append("\n");
                } catch (Exception e) {
                    contentForSummary.append(msgJson).append("\n");
                }
            }
            
            String newSummary = generateSummary(contentForSummary.toString(), userId);
            
            if (newSummary == null || newSummary.isEmpty()) {
                log.warn("[上下文压缩] 摘要生成失败，保留原始消息，userId={}, sessionId={}", userId, sessionId);
                return;
            }
            
            stringRedisTemplate.delete(memoryKey);
            
            String summaryKey = SUMMARY_PREFIX + type + ":" + userId + ":" + sessionId;
            stringRedisTemplate.opsForValue().set(summaryKey, newSummary, 14, TimeUnit.DAYS);
            log.info("[上下文压缩] 摘要已保存，key={}", summaryKey);
            
            String summaryMessage = String.format(
                    "{\"messageType\":\"SYSTEM\",\"text\":\"【历史对话摘要】%s\"}", 
                    newSummary.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"));
            stringRedisTemplate.opsForList().rightPush(memoryKey, summaryMessage);
            
            for (String msg : messagesToKeep) {
                stringRedisTemplate.opsForList().rightPush(memoryKey, msg);
            }
            
            stringRedisTemplate.expire(memoryKey, 14, TimeUnit.DAYS);
            
            log.info("[上下文压缩] 压缩完成，原始消息数={}, 压缩后=1条摘要+{}条消息, 压缩了={}条",
                    allMessages.size(), messagesToKeep.size(), messagesToCompress);
                    
        } catch (Exception e) {
            log.error("[上下文压缩] 压缩失败，userId={}, sessionId={}", userId, sessionId, e);
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    @Override
    public String getSummary(String userId, String sessionId, String type) {
        String summaryKey = SUMMARY_PREFIX + type + ":" + userId + ":" + sessionId;
        return stringRedisTemplate.opsForValue().get(summaryKey);
    }

    @Override
    public void clearSummary(String userId, String sessionId, String type) {
        String summaryKey = SUMMARY_PREFIX + type + ":" + userId + ":" + sessionId;
        stringRedisTemplate.delete(summaryKey);
        log.info("[上下文压缩] 摘要已清除，key={}", summaryKey);
    }

    private String generateSummary(String content, String userId) {
        try {
            String prompt = String.format(SUMMARY_PROMPT, content);
            ChatClient chatClient = chatClientFactory.getDefaultChatClient(Long.parseLong(userId));
            String summary = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            return summary != null ? summary.trim() : null;
        } catch (Exception e) {
            log.error("[上下文压缩] 生成摘要失败: {}", e.getMessage());
            return null;
        }
    }
}
