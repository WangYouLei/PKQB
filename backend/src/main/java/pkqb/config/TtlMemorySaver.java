package pkqb.config;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;

import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 带TTL淘汰的MemorySaver
 * 官方MemorySaver的所有Agent会话checkpoint永久驻留堆内且无公开清理API（remove为protected final、put为public final），
 * 此子类通过可覆写的insert/updated钩子记录各会话最后写入时间，
 * 由定时任务（AgentSessionCleaner）周期性淘汰长期不活跃的会话，防止内存泄漏
 */
public class TtlMemorySaver extends MemorySaver {

    // 会话空闲超过该时长后可被淘汰（与Redis聊天历史7天TTL对齐）
    private static final long IDLE_TTL_MS = 7L * 24 * 3600 * 1000;

    // threadId -> 最后一次写入checkpoint的时间戳
    private final Map<String, Long> lastActiveAt = new ConcurrentHashMap<>();

    @Override
    protected void insertedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
        super.insertedCheckpoint(config, checkpoints, checkpoint);
        touch(config);
    }

    @Override
    protected void updatedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
        super.updatedCheckpoint(config, checkpoints, checkpoint);
        touch(config);
    }

    private void touch(RunnableConfig config) {
        config.threadId().ifPresent(threadId -> lastActiveAt.put(threadId, System.currentTimeMillis()));
    }

    /**
     * 淘汰超过TTL未活跃的会话checkpoint
     *
     * @return 本次清理的会话数
     */
    public int evictIdleThreads() {
        long now = System.currentTimeMillis();
        int evicted = 0;
        for (Map.Entry<String, Long> entry : lastActiveAt.entrySet()) {
            if (now - entry.getValue() > IDLE_TTL_MS) {
                remove(entry.getKey());
                lastActiveAt.remove(entry.getKey());
                evicted++;
            }
        }
        return evicted;
    }
}
