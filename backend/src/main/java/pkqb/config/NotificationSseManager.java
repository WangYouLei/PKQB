package pkqb.config;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SSE 连接管理器
 * 管理用户 SSE 连接，支持向指定用户推送通知消息
 * 支持同一用户多端连接
 */
@Slf4j
@Component
public class NotificationSseManager {

    /** userId -> 该用户的所有 SseEmitter 列表（支持多端连接） */
    private static final Map<Long, CopyOnWriteArrayList<SseEmitter>> EMITTERS = new ConcurrentHashMap<>();

    /** 心跳间隔（秒），需小于常见代理的空闲超时（Nginx 默认 60s） */
    private static final long HEARTBEAT_SECONDS = 25;

    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public NotificationSseManager() {
        // 定时向所有连接发送注释行心跳，防止代理断开空闲连接
        heartbeatScheduler.scheduleAtFixedRate(this::sendHeartbeat, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void destroy() {
        heartbeatScheduler.shutdownNow();
    }

    /**
     * 注册新的 SSE 连接
     */
    public SseEmitter register(Long userId) {
        SseEmitter emitter = new SseEmitter(0L); // 不超时
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(e -> remove(userId, emitter));

        EMITTERS.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        log.info("[SSE] 用户连接成功, userId={}, 当前在线人数={}", userId, EMITTERS.size());

        // 立即发一条注释行，让客户端尽早确认连接建立
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            remove(userId, emitter);
        }
        return emitter;
    }

    /**
     * 向指定用户发送通知消息（推送到该用户的所有活跃连接）
     * @param userId 目标用户ID
     * @param message JSON 格式的消息内容
     * @return 是否至少有一个连接发送成功
     */
    public boolean sendToUser(Long userId, String message) {
        CopyOnWriteArrayList<SseEmitter> userEmitters = EMITTERS.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            log.debug("[SSE] 用户不在线，跳过推送, userId={}", userId);
            return false;
        }

        boolean anySuccess = false;
        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(message, MediaType.APPLICATION_JSON));
                anySuccess = true;
            } catch (Exception e) {
                log.error("[SSE] 消息推送失败, userId={}, error={}", userId, e.getMessage());
                remove(userId, emitter);
            }
        }
        return anySuccess;
    }

    /**
     * 检查用户是否在线
     */
    public boolean isUserOnline(Long userId) {
        return !EMITTERS.getOrDefault(userId, new CopyOnWriteArrayList<>()).isEmpty();
    }

    /**
     * 获取当前在线用户数
     */
    public int getOnlineCount() {
        return EMITTERS.size();
    }

    private void remove(Long userId, SseEmitter emitter) {
        List<SseEmitter> userEmitters = EMITTERS.get(userId);
        if (userEmitters != null) {
            userEmitters.remove(emitter);
            if (userEmitters.isEmpty()) {
                EMITTERS.remove(userId);
                log.info("[SSE] 用户断开连接, userId={}, 当前在线人数={}", userId, EMITTERS.size());
            }
        }
    }

    private void sendHeartbeat() {
        EMITTERS.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    remove(userId, emitter);
                }
            }
        });
    }
}
