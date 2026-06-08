package pkqb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * WebSocket 通知处理器
 * 管理用户 WebSocket 连接，支持向指定用户推送通知消息
 * 支持同一用户多端连接
 */
@Slf4j
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    /** userId -> 该用户的所有 WebSocketSession 列表（支持多端连接） */
    private static final Map<Long, CopyOnWriteArrayList<WebSocketSession>> SESSIONS = new ConcurrentHashMap<>();

    /**
     * 连接建立后，将 session 按 userId 存储
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = getUserId(session);
        if (userId != null) {
            SESSIONS.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(session);
            log.info("[WebSocket] 用户连接成功, userId={}, sessionId={}, 当前在线人数={}",
                    userId, session.getId(), SESSIONS.size());
        }
    }

    /**
     * 连接关闭后，移除对应的 session
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long userId = getUserId(session);
        if (userId != null) {
            CopyOnWriteArrayList<WebSocketSession> userSessions = SESSIONS.get(userId);
            if (userSessions != null) {
                userSessions.remove(session);
                // 如果该用户没有活跃连接了，移除整个列表
                if (userSessions.isEmpty()) {
                    SESSIONS.remove(userId);
                }
            }
            log.info("[WebSocket] 用户断开连接, userId={}, 当前在线人数={}", userId, SESSIONS.size());
        }
    }

    /**
     * 处理传输错误
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        Long userId = getUserId(session);
        log.error("[WebSocket] 传输错误, userId={}, error={}", userId, exception.getMessage());
        if (session.isOpen()) {
            session.close();
        }
        if (userId != null) {
            CopyOnWriteArrayList<WebSocketSession> userSessions = SESSIONS.get(userId);
            if (userSessions != null) {
                userSessions.remove(session);
                if (userSessions.isEmpty()) {
                    SESSIONS.remove(userId);
                }
            }
        }
    }

    /**
     * 向指定用户发送通知消息（推送到该用户的所有活跃连接）
     * @param userId 目标用户ID
     * @param message JSON 格式的消息内容
     * @return 是否至少有一个连接发送成功
     */
    public boolean sendToUser(Long userId, String message) {
        CopyOnWriteArrayList<WebSocketSession> userSessions = SESSIONS.get(userId);
        if (userSessions == null || userSessions.isEmpty()) {
            log.debug("[WebSocket] 用户不在线，跳过推送, userId={}", userId);
            return false;
        }

        boolean anySuccess = false;
        // 收集已断开的 session 用于清理
        List<WebSocketSession> deadSessions = new java.util.ArrayList<>();

        for (WebSocketSession session : userSessions) {
            if (!session.isOpen()) {
                deadSessions.add(session);
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(message));
                }
                anySuccess = true;
            } catch (IOException e) {
                log.error("[WebSocket] 消息推送失败, userId={}, sessionId={}, error={}",
                        userId, session.getId(), e.getMessage());
                deadSessions.add(session);
            }
        }

        // 清理已断开的 session
        if (!deadSessions.isEmpty()) {
            userSessions.removeAll(deadSessions);
            if (userSessions.isEmpty()) {
                SESSIONS.remove(userId);
            }
        }

        if (anySuccess) {
            log.debug("[WebSocket] 消息推送成功, userId={}", userId);
        }
        return anySuccess;
    }

    /**
     * 检查用户是否在线
     */
    public boolean isUserOnline(Long userId) {
        CopyOnWriteArrayList<WebSocketSession> userSessions = SESSIONS.get(userId);
        if (userSessions == null || userSessions.isEmpty()) {
            return false;
        }
        return userSessions.stream().anyMatch(WebSocketSession::isOpen);
    }

    /**
     * 获取当前在线用户数
     */
    public int getOnlineCount() {
        return SESSIONS.size();
    }

    /**
     * 从 WebSocket Session 属性中获取 userId
     */
    private Long getUserId(WebSocketSession session) {
        Object userId = session.getAttributes().get("userId");
        return userId instanceof Long ? (Long) userId : null;
    }
}
