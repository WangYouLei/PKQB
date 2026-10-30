package pkqb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TagField;

import java.util.List;

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

    /** 权限元数据字段（RAG数据隔离）：TAG类型仅做等值匹配，字段名与 RagDataIsolationServiceImpl 中的常量一致 */
    private static final List<SchemaField> ISOLATION_METADATA_FIELDS = List.of(
            TagField.of("$.metadata.userId").as("userId"),
            TagField.of("$.metadata.classId").as("classId"),
            TagField.of("$.metadata.isPrivate").as("isPrivate"),
            TagField.of("$.metadata.fileId").as("fileId")
    );

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
                .metadataFields(
                        RedisVectorStore.MetadataField.tag("userId"),
                        RedisVectorStore.MetadataField.tag("classId"),
                        RedisVectorStore.MetadataField.tag("isPrivate"),
                        RedisVectorStore.MetadataField.tag("fileId"))
                .initializeSchema(initializeSchema)
                .build();

        // 先确保索引按新schema建立（索引已存在时为空操作），再对旧索引补齐权限字段
        vectorStore.afterPropertiesSet();
        alterIndexAddIsolationFields(jedisPooled);

        log.info("[RedisVectorStore] RedisVectorStore 创建成功");
        return vectorStore;
    }

    /**
     * 对已存在的旧索引执行 FT.ALTER 追加权限元数据字段。
     * initializeSchema 只在索引不存在时建索引，存量索引不会自动获得新字段；
     * 字段已存在（Duplicate field）或索引刚按新schema建立时忽略异常。
     */
    private void alterIndexAddIsolationFields(JedisPooled jedisPooled) {
        try {
            jedisPooled.ftAlter(indexName, ISOLATION_METADATA_FIELDS);
            log.info("[RedisVectorStore] 索引 {} 已通过 FT.ALTER 补齐权限元数据字段", indexName);
        } catch (JedisDataException e) {
            log.info("[RedisVectorStore] 索引 {} 无需补字段（不存在或已含权限字段）: {}", indexName, e.getMessage());
        }
    }
}
