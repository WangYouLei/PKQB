package pkqb.config;

import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Agent会话checkpoint定时清理任务
 * 所有用户Agent共享的MemorySaver无淘汰机制，长期运行内存只增不减；
 * 此任务每小时清理一次超过7天不活跃的会话checkpoint
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentSessionCleaner {

    private final MemorySaver memorySaver;

    // 每小时执行一次，启动1小时后开始
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
    public void evictIdleSessions() {
        if (memorySaver instanceof TtlMemorySaver saver) {
            int evicted = saver.evictIdleThreads();
            if (evicted > 0) {
                log.info("[Agent会话清理] 清理 {} 个不活跃会话checkpoint", evicted);
            }
        }
    }
}
