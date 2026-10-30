package pkqb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pkqb.mapper.UserMapper;
import pkqb.pojo.entity.UserEntity;
import pkqb.service.RagDataIsolationService;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.json.Path2;
import redis.clients.jedis.search.FTSearchParams;
import redis.clients.jedis.search.SearchResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG 数据隔离服务实现（RAG 知识库检索侧权限的唯一入口）。
 * <p>
 * 隔离语义与文件模块严格对齐（见 FileServiceImpl#getUserFiles / #getClassPublicFiles）：
 * 本人上传的文档全部可见（含私有）；当前同班成员上传的非私有文档可见。
 * <p>
 * 设计说明、方案取舍与后期修改指引即本 Javadoc（第一至五节）；
 * 其中"已知限制"对应的优化台账条目见 {@code docs/后期优化清单.md} 第 2.4 节。
 *
 * <h2>一、向量库元数据字段</h2>
 * 以下字段在 RedisVectorStoreConfig 中以 TAG 类型建立索引（仅支持等值匹配），
 * 全部落在 RedisJSON 的 {@code $.metadata.xxx} 路径下：
 * <pre>
 * 字段       含义                          是否参与检索过滤
 * userId     上传者用户ID                  是（永远可见自己的文档）
 * isPrivate  是否私有                      是（同班可见性受其约束）
 * fileId     文件记录ID（file 表主键）      否，仅用于"按文件"同步/清理
 * classId    上传时刻的班级归属（快照）      否，仅留痕，不进入过滤路径
 * </pre>
 *
 * <h2>二、字段按"变更频率"分级处理（这是本设计最核心的取舍）</h2>
 * 向量库是 Redis 中的物化视图，不具备 join 能力，因此每个字段都要在
 * "入库时快照"与"运行时实时计算"之间二选一。取舍标准是变更频率：
 * <pre>
 * 变更频率     字段        策略                                    一致性问题
 * 永不变        userId      入库写入即可，删除文件时整体清理        无
 * 极低频        classId     不进入过滤路径；检索时运行时 join 现查   无（换班即时生效）
 * 低频          isPrivate   入库快照 + 变更时 JSON.SET 精准回写      短暂不一致，可重试
 * 生命周期      文件删除     按 fileId 做 IN 过滤批量清理              残留片段仅本人/本班可见
 * </pre>
 * <b>为什么 classId 不进过滤路径</b>（本项目最关键的一个决定）：
 * 如果把 classId 作为过滤条件（{@code classId == 5}），它就变成了"上传时刻班级"的
 * 冻结快照；而文件列表接口是实时查 user 表的。两者在用户换班后会产生双向漂移——
 * 对老班级：他的文档仍按老 classId 匹配，老班同学能继续看到（比文件列表宽松）；
 * 对新班级：他的文档 classId 仍是老的，新班同学看不到（比文件列表严格）。
 * 因此本实现改为：检索时实时查 user 表拿到"当前同班成员ID集合"，再断言文档
 * 归属者是否在该集合中，从构造上消除漂移，单一事实源（user 表），且无需回写向量库。
 * <p>
 * 曾评估但放弃的方案：
 * <pre>
 * 方案二 事件驱动回写（换班时 JSON.SET 改全部片段 + 定时对账任务）
 *        缺点：绕过 VectorStore 抽象、需要全量扫片段、对账任务本身要处理漂移和并发，
 *              复杂度和故障面远高于"每次检索多查一次只 select id 的 SQL"。
 * 方案三 file 表增加 class_id 快照列（把语义搬到文件侧，与文件一起冻结）
 *        缺点：等于确认"文档归属上传时刻的班级"，会改变产品语义（换班后老班同学
 *              仍能看我的文件），且两张表都要维护快照，一致性面更大。
 * </pre>
 *
 * <h2>三、检索过滤表达式</h2>
 * 由 {@link #buildFilterExpression(Long)} 生成，示意：
 * <pre>
 * userId == 1 || (isPrivate == false && userId in [1, 2, 5])
 *          ^--- 本人全部文档（含私有）    ^--- 当前同班成员的非私有文档
 * </pre>
 * 用户未加入班级时降级为 {@code userId == 1}（仅本人可见）；
 * userId 为空时返回 {@code userId == -1}（fail-closed，匹配不到任何文档）。
 *
 * <h2>四、已知限制（改动前请先读）</h2>
 * <ol>
 *   <li><b>存量数据不可检索</b>：本方案上线前已入库的片段没有 userId/isPrivate 元数据，
 *       过滤表达式匹配不到它们，表现为"知识库突然检索不到旧内容"，需重新上传文件。</li>
 *   <li><b>每次检索多一次 MySQL 查询</b>：运行时 join 每次问答执行一次
 *       {@code select id from user where class_id = ?}，走索引且只取主键，量级可接受。
 *       若要进一步降低开销，可在 RAG 会话级缓存同班用户集合（需接受缓存期内换班延迟）。</li>
 *   <li><b>IN 表达式随班级人数线性增长</b>：{@code userId in [...]} 的长度与班级人数成正比，
 *       班级规模上百后请求体明显变长。若班级达到千人级，应切回
 *       "元数据快照 + 换班事件回写 + 对账"（即上文方案二）。</li>
 *   <li><b>isPrivate 同步是尽力而为</b>：{@link #updateDocumentsPrivacy} 失败仅告警，
 *       向量库元数据保持旧值，用户重新切换一次隐私状态即可重试；不阻断文件更新。</li>
 *   <li><b>classId 元数据保留但不参与过滤</b>：留痕用于排查（如核对上传时班级），
 *       不要误以为它参与权限判断。</li>
 *   <li><b>仅覆盖 RAG 知识库</b>：试卷/题库等模块的可见性由各自 Service 判断，不经过本服务。</li>
 * </ol>
 *
 * <h2>五、后期修改指引</h2>
 * <pre>
 * 要改的东西                         需要修改的位置
 * 过滤语义（谁能看到什么）            只改 buildFilterExpression，其余不动
 * 新增一个隔离字段                    ① RedisVectorStoreConfig.ISOLATION_METADATA_FIELDS
 *                                    ② RedisVectorStoreConfig.builder 中的同名 MetadataField.tag(...)
 *                                    ③ 本类 META_* 常量 + buildUploadMetadata 写入
 *                                    ④（若参与过滤）buildFilterExpression 中加入断言
 *                                    ⑤ 已存在索引需 FT.ALTER 补字段，重启即可自动迁移
 * 文件删除/隐私变更时的同步时机        FileServiceImpl（deleteFile / batchDeleteFiles / updateFile）
 * 检索身份缺失时的行为（fail-closed）  RagAgentHook.beforeAgent 与 AiQuestionSolver#searchVectorStore
 * 上传时写入哪些元数据                 SpringAiAlibabaServiceImpl#processDocumentsContent（唯一写入口）
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagDataIsolationServiceImpl implements RagDataIsolationService {

    public static final String META_USER_ID = "userId";
    public static final String META_CLASS_ID = "classId";
    public static final String META_IS_PRIVATE = "isPrivate";
    public static final String META_FILE_ID = "fileId";

    /** 隐私同步时按 fileId 定位片段的分页大小 */
    private static final int SEARCH_PAGE_SIZE = 100;

    private final UserMapper userMapper;
    private final VectorStore vectorStore;
    private final JedisPooled jedisPooled;

    @Value("${spring.ai.vectorstore.redis.index-name:spring-ai-index}")
    private String indexName;

    @Override
    public Map<String, Object> buildUploadMetadata(Long userId, Long fileId) {
        UserEntity user = userId != null ? userMapper.selectById(userId) : null;
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(META_USER_ID, userId);
        if (user != null && user.getClassId() != null) {
            metadata.put(META_CLASS_ID, user.getClassId().longValue());
        }
        // 知识库上传默认班级共享，与 doAddDocuments 写 file 表的 isPrivate=false 保持一致
        metadata.put(META_IS_PRIVATE, Boolean.FALSE);
        metadata.put(META_FILE_ID, fileId);
        return metadata;
    }

    @Override
    public String buildFilterExpression(Long userId) {
        if (userId == null) {
            // 不应发生（调用方已做身份校验），防御性返回不匹配任何文档的表达式
            return META_USER_ID + " == -1";
        }
        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getClassId() == null) {
            return META_USER_ID + " == " + userId;
        }
        // 运行时取当前同班成员集合（@TableLogic 自动过滤已删除用户），换班即时生效
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(UserEntity::getId)
               .eq(UserEntity::getClassId, user.getClassId());
        List<Long> classUserIds = userMapper.selectList(wrapper).stream()
                .map(UserEntity::getId)
                .toList();
        if (classUserIds.size() <= 1) {
            // 班级无其他成员，仅本人文档可见
            return META_USER_ID + " == " + userId;
        }
        String inList = classUserIds.stream().map(String::valueOf).collect(Collectors.joining(", "));
        // 本人全部文档（含私有）+ 当前同班成员的非私有文档，语义与 getClassPublicFiles 实时对齐
        return META_USER_ID + " == " + userId
                + " || (" + META_IS_PRIVATE + " == false && " + META_USER_ID + " in [" + inList + "])";
    }

    @Override
    public void updateDocumentsPrivacy(Long fileId, boolean isPrivate) {
        if (fileId == null) {
            return;
        }
        try {
            // 先分页收集该文件全部片段的 key（文档 id 即带前缀的完整 Redis key）
            String query = "@" + META_FILE_ID + ":{" + fileId + "}";
            List<String> docKeys = new ArrayList<>();
            int offset = 0;
            while (true) {
                SearchResult result = jedisPooled.ftSearch(indexName, query,
                        FTSearchParams.searchParams().limit(offset, SEARCH_PAGE_SIZE));
                result.getDocuments().forEach(d -> docKeys.add(d.getId()));
                if (result.getDocuments().size() < SEARCH_PAGE_SIZE) {
                    break;
                }
                offset += SEARCH_PAGE_SIZE;
            }
            // 再统一改写元数据（两阶段，避免边遍历边修改索引字段）
            for (String key : docKeys) {
                jedisPooled.jsonSet(key, Path2.of("$.metadata." + META_IS_PRIVATE), isPrivate);
            }
            log.info("[RAG数据隔离] 已同步文件隐私状态到向量片段, fileId={}, isPrivate={}, 片段数={}",
                    fileId, isPrivate, docKeys.size());
        } catch (Exception e) {
            // 尽力而为：失败不阻断文件更新，元数据保持旧值，可重新切换一次隐私状态触发重试
            log.warn("[RAG数据隔离] 隐私状态同步向量片段失败, fileId={}, isPrivate={}: {}",
                    fileId, isPrivate, e.getMessage());
        }
    }

    @Override
    public void deleteDocumentsByFileId(Long fileId) {
        if (fileId != null) {
            deleteDocumentsByFileIds(List.of(fileId));
        }
    }

    @Override
    public void deleteDocumentsByFileIds(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        try {
            Filter.Expression filter = new Filter.Expression(
                    Filter.ExpressionType.IN,
                    new Filter.Key(META_FILE_ID),
                    new Filter.Value((Object) fileIds));
            vectorStore.delete(filter);
            log.info("[RAG数据隔离] 已同步清理 {} 个文件的向量片段, fileIds={}", fileIds.size(), fileIds);
        } catch (Exception e) {
            // 尽力而为：清理失败不阻断文件删除流程，残留片段因权限过滤仅本人/本班可见
            log.warn("[RAG数据隔离] 向量片段清理失败, fileIds={}: {}", fileIds, e.getMessage());
        }
    }
}
