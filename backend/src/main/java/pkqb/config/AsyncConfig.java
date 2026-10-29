package pkqb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步与定时任务配置
 * 1. 启用 @Async 注解支持：此前工程未开启 @EnableAsync，AuditLogServiceImpl 等处的 @Async 实际同步执行
 * 2. 启用 @Scheduled 定时任务支持（如 AgentSessionCleaner 的会话checkpoint清理）
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：日常审计日志等轻量任务足够
        executor.setCorePoolSize(4);
        // 最大线程数：高峰期扩容上限
        executor.setMaxPoolSize(8);
        // 队列容量：超出后触发扩线程，再超出走拒绝策略
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("async-task-");
        // 队列满且线程达上限时由调用线程执行，保证任务不丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
