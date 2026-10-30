package pkqb.service;

import java.util.List;
import java.util.Map;

/**
 * RAG 知识库数据隔离服务
 * <p>
 * 以向量库 metadata 过滤表达式实现检索侧数据隔离，语义与文件模块对齐：
 * 本人上传的文档可见；同班级成员上传的非私有文档可见。
 * <p>
 * 设计说明、字段分级策略、方案取舍与已知限制见实现类
 * {@link pkqb.service.impl.RagDataIsolationServiceImpl} 的 Javadoc（第一至五节）；
 * 优化台账条目见 {@code docs/后期优化清单.md} 第 2.4 节。
 * 改动过滤语义只需改 {@link #buildFilterExpression(Long)}；
 * 新增隔离字段需同时改 RedisVectorStoreConfig 的索引声明与本实现的上传元数据。
 */
public interface RagDataIsolationService {

    /**
     * 构建上传文档片段时写入的权限元数据。
     * 同一文件的所有片段共享同一份元数据。
     *
     * @param userId 上传者用户ID
     * @param fileId 文件记录ID（file 表主键）
     * @return 权限元数据
     */
    Map<String, Object> buildUploadMetadata(Long userId, Long fileId);

    /**
     * 按当前用户身份构建检索过滤表达式，语义与文件列表实时对齐：
     * 本人全部文档（含私有）+ 当前同班成员的非私有文档。
     * 同班成员集合在检索时实时查询 user 表（运行时 join），
     * 用户换班后对旧班/新班的可见性即时生效，向量库元数据无需回写。
     * 用户未加入班级时仅能检索本人文档。
     *
     * @param userId 当前用户ID
     * @return Spring AI 过滤表达式字符串，如 {@code userId == 1 || (isPrivate == false && userId in [1, 2, 5])}
     */
    String buildFilterExpression(Long userId);

    /**
     * 同步更新指定文件全部向量片段的 isPrivate 元数据（文件隐私状态变更时调用）。
     * 直接改写 RedisJSON 元数据字段，不触发重新 embedding；尽力而为，失败仅告警。
     */
    void updateDocumentsPrivacy(Long fileId, boolean isPrivate);

    /**
     * 删除指定文件在向量库中的全部文档片段（文件删除时同步清理，避免已删文件仍可被检索）。
     * 尽力而为：清理失败不抛出，残留片段因权限过滤仅本人/本班可见。
     */
    void deleteDocumentsByFileId(Long fileId);

    /**
     * 批量删除多个文件在向量库中的文档片段。
     */
    void deleteDocumentsByFileIds(List<Long> fileIds);
}
