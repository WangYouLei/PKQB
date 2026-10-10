package pkqb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import redis.clients.jedis.JedisPooled;

/**
 * Redis向量存储配置类
 * 配置Redis作为向量数据库，用于RAG知识库存储
 */
@Configuration
@Slf4j
public class RedisVectorStoreConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.ai.vectorstore.redis.index-name:spring-ai-index}")
    private String indexName;

    @Value("${spring.ai.vectorstore.redis.prefix:embedding:}")
    private String prefix;

    @Value("${spring.ai.vectorstore.redis.initialize-schema:false}")
    private boolean initializeSchema;

    @Bean
    public JedisPooled jedisPooled() {
        log.info("[RedisVectorStore] 创建 JedisPooled 连接, host={}, port={}", redisHost, redisPort);
        return new JedisPooled(redisHost, redisPort);
    }

    @Bean
    public RedisVectorStore vectorStore(JedisPooled jedisPooled, EmbeddingModel embeddingModel) {
        log.info("[RedisVectorStore] 创建 RedisVectorStore, indexName={}, prefix={}, initializeSchema={}", 
                indexName, prefix, initializeSchema);
        
        RedisVectorStore vectorStore = RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName(indexName)
                .prefix(prefix)
                .initializeSchema(initializeSchema)
                .build();
        
        log.info("[RedisVectorStore] RedisVectorStore 创建成功");
        return vectorStore;
    }
}
