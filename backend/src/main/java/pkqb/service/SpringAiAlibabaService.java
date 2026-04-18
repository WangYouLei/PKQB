package pkqb.service;

import org.springframework.web.multipart.MultipartFile;
import pkqb.common.Result;
import pkqb.pojo.dto.AiRubric;
import reactor.core.publisher.Flux;

import java.util.List;

public interface SpringAiAlibabaService {
    /**
     * 添加文档
     *
     * @param file 文档文件
     * @param userId 用户ID
     * @return 添加结果
     */
    Result<String> addDocuments(MultipartFile file, Long userId);


    /**
     * 使用RAG从Milvus检索信息并回答问题
     *
     * @param query     问题
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @return 回答结果
     */
    Flux<String> ragQuery(String query, String sessionId, Long userId);

    /**
     * 获取历史对话
     *
     * @param userId 用户id
     * @param type 对话类型   rag或者chat
     * @return 历史对话
     */
    Result<List<Object>> getHistory(String userId,String type);

    /**
     * 使用ChatClient回答问题
     *
     * @param query     问题
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @return 回答结果
     */
    Flux<String> query(String query, String sessionId, Long userId);



    /**
     * 添加文档（题目）
     *
     * @param content 文档内容
     * @return 添加结果
     */
    Result<String> addRubric(String content);

    /**
     * 获取具体的历史对话  根据sessionId获取
     *
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @param type 对话类型   rag或者chat
     * @return 历史对话
     */
    Result<Object> getHistoryBySessionId(String sessionId, String userId, String type);

    /**
     * 上传题库文件（AI解析）
     *
     * @param file 文件
     * @param userId 用户ID
     * @return 添加结果
     */
    Result<List<AiRubric>> handleRubricFile(MultipartFile file, Long userId);

    /**
     * 上传题库文件（本地解析）
     *
     * @param file 文件
     * @param userId 用户ID
     * @return 添加结果
     */
    Result<List<AiRubric>> handleRubricFileLocal(MultipartFile file, Long userId);

    /**
     * 删除指定的聊天会话记录
     *
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @param type      对话类型   rag或者chat
     * @return 删除结果
     */
    Result<Boolean> deleteHistory(String sessionId, Long userId, String type);
}
