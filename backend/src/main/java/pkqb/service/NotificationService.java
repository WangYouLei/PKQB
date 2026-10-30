package pkqb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pkqb.config.NotificationSseManager;
import pkqb.mapper.NotificationMapper;
import pkqb.pojo.entity.NotificationEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationSseManager sseManager;
    private final NotificationMapper notificationMapper;
    private final ObjectMapper objectMapper;

    public void notify(Long userId, String type, String title, String message) {
        // 1. 保存到数据库
        NotificationEntity entity = new NotificationEntity();
        entity.setUserId(userId);
        entity.setType(type);
        entity.setTitle(title);
        entity.setMessage(message);
        entity.setIsRead(false);
        entity.setCreateTime(LocalDateTime.now());
        try {
            notificationMapper.insert(entity);
        } catch (Exception e) {
            log.error("[通知] 保存到数据库失败, userId={}, type={}", userId, type, e);
        }

        // 2. SSE 实时推送
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("id", entity.getId());
            payload.put("type", type);
            payload.put("title", title);
            payload.put("message", message);
            payload.put("timestamp", entity.getCreateTime().toString());
            payload.put("read", false);

            String json = objectMapper.writeValueAsString(payload);
            boolean sent = sseManager.sendToUser(userId, json);

            if (sent) {
                log.info("[通知推送] userId={}, type={}, title={}", userId, type, title);
            } else {
                log.info("[通知推送] 用户不在线，已存库, userId={}, type={}", userId, type);
            }
        } catch (JsonProcessingException e) {
            log.error("[通知推送] JSON序列化失败, userId={}, type={}", userId, type, e);
        }
    }

    public void notifyRubricParseComplete(Long userId, String finalMethod) {
        notify(userId, "RUBRIC_PARSE_COMPLETE", "题目解析完成",
                "本次采用「" + finalMethod + "」解析完成，请查看结果");
    }

    /**
     * 解析过程中发生降级但最终成功：告知用户失败的模型及原因、最终采用的解析方式，并提示检查模型额度
     */
    public void notifyRubricParseCompleteWithFallback(Long userId, String failedModel, String failReason, String finalMethod) {
        notify(userId, "RUBRIC_PARSE_COMPLETE", "题目解析完成（已自动降级）",
                failedModel + "解析失败：" + failReason
                        + "，已自动降级为「" + finalMethod + "」并解析成功，请查看结果。"
                        + "建议检查" + failedModel + "是否还有额度");
    }

    public void notifyRubricParseFailed(Long userId, String reason) {
        notify(userId, "RUBRIC_PARSE_FAILED", "题目解析失败",
                "AI 解析失败：" + reason + "。建议检查对应模型是否还有额度");
    }

    public void notifyHtmlGenerateComplete(Long userId, String rubricTitle) {
        notify(userId, "HTML_GENERATE_COMPLETE", "HTML生成完成",
                "试卷「" + rubricTitle + "」HTML 文件已生成，请在文件列表中查看");
    }

    public void notifyKnowledgeUploadComplete(Long userId, String fileName) {
        notify(userId, "KNOWLEDGE_UPLOAD_COMPLETE", "知识库上传完成",
                "文档「" + fileName + "」已成功上传到知识库");
    }

    public void notifyKnowledgeUploadFailed(Long userId, String fileName, String reason) {
        notify(userId, "KNOWLEDGE_UPLOAD_FAILED", "知识库上传失败",
                "文档「" + fileName + "」上传失败：" + reason);
    }

    // ========== REST API 方法 ==========

    public List<NotificationEntity> getNotifications(Long userId) {
        LambdaQueryWrapper<NotificationEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NotificationEntity::getUserId, userId)
               .orderByDesc(NotificationEntity::getCreateTime)
               .last("LIMIT 100");
        return notificationMapper.selectList(wrapper);
    }

    public void markAsRead(Long notificationId, Long userId) {
        NotificationEntity entity = notificationMapper.selectById(notificationId);
        if (entity == null) {
            throw new IllegalArgumentException("通知不存在");
        }
        if (!entity.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作");
        }
        entity.setIsRead(true);
        notificationMapper.updateById(entity);
    }

    public void markAllAsRead(Long userId) {
        LambdaUpdateWrapper<NotificationEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(NotificationEntity::getUserId, userId)
               .eq(NotificationEntity::getIsRead, false)
               .set(NotificationEntity::getIsRead, true);
        notificationMapper.update(null, wrapper);
    }

    public void deleteNotification(Long notificationId, Long userId) {
        NotificationEntity entity = notificationMapper.selectById(notificationId);
        if (entity == null) {
            throw new IllegalArgumentException("通知不存在");
        }
        if (!entity.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作");
        }
        notificationMapper.deleteById(notificationId);
    }

    public void clearAll(Long userId) {
        LambdaQueryWrapper<NotificationEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NotificationEntity::getUserId, userId);
        notificationMapper.delete(wrapper);
    }
}
