package pkqb.service;

import org.springframework.web.multipart.MultipartFile;
import pkqb.common.Result;
import pkqb.pojo.dto.AiRubric;
import pkqb.pojo.dto.RubricParseTaskStatus;
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
     * 使用RAG从Redis向量数据库检索信息并回答问题
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
    Result<List<String>> getHistory(String userId,String type);

    /**
     * 使用ReactAgent回答问题
     *
     * @param query     问题
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @return 回答结果
     */
    Flux<String> query(String query, String sessionId, Long userId);



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
     * 提交题目文件异步解析任务（AI解析）：后台执行，立即返回任务ID
     *
     * @param filename  原始文件名
     * @param fileBytes 文件内容（同步阶段读出，避免异步阶段临时文件被清理）
     * @param userId    用户ID
     * @param modelType 模型类型：2=视觉模型，1=纯文本，null=自动选择
     * @return 任务ID
     */
    Result<String> submitRubricFileParse(String filename, byte[] fileBytes, Long userId, Integer modelType);

    /**
     * 查询题目文件异步解析任务状态（含进度与结果）
     *
     * @param taskId 任务ID
     * @param userId 用户ID（校验任务归属）
     * @return 任务状态
     */
    Result<RubricParseTaskStatus> getRubricParseTask(String taskId, Long userId);

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

    /**
     * 删除会话中的指定消息
     *
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @param type      对话类型 rag或chat
     * @param messageIndices 消息索引列表（从0开始）
     * @return 删除结果
     */
    Result<Boolean> deleteMessages(String sessionId, Long userId, String type, List<Integer> messageIndices);

    /**
     * AI解答题目 - 生成答案、解析或计算步骤
     *
     * @param questionText 题目内容
     * @param questionType 题目类型
     * @param optionsJson  选项JSON
     * @param generateType 生成类型：answer/explanation/steps
     * @param userId       用户ID
     * @return 生成结果
     */
    Result<String> aiSolveQuestion(String questionText, String questionType, String optionsJson, String generateType, Long userId);
}
