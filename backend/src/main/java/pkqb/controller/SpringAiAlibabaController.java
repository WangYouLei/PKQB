package pkqb.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pkqb.common.Result;
import pkqb.pojo.dto.AiRubric;
import pkqb.service.SpringAiAlibabaService;
import reactor.core.publisher.Flux;
import java.util.List;

/**
 * Spring AI Alibaba 控制器
 */
@RestController
@Slf4j
@RequestMapping("/api/ai")
public class SpringAiAlibabaController {

  private final SpringAiAlibabaService saaService;

    public SpringAiAlibabaController(SpringAiAlibabaService saaService) {
        this.saaService = saaService;
    }

    /**
     * 添加文档（知识库）
     * @param file 文档文件
     * @return 添加结果
     */
    @PostMapping(value = "/add-documentsFile")
    public Result<String> addDocumentsFile(@RequestParam("file") MultipartFile file) {
        return saaService.addDocuments(file);
    }


    /**处理上传的"问题"文件
     * @param file
     * @return
     */
    @PostMapping("/handle-rubricFile")
    public Result<List<AiRubric>> handleRubricFile(@RequestParam("file") MultipartFile file) {
        return saaService.handleRubricFile(file);
    }

    /**
     * 使用ChatClient回答问题
     * @param query 问题
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @return 回答结果
     */
    @GetMapping(value = "/query", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> query(@RequestParam("query") String query,
                              @RequestParam("sessionId")String sessionId,
                              @RequestParam("userId") String userId
    ) {
        return saaService.query(query, sessionId, userId);
    }

    /**
     * 使用RAG从Milvus检索信息并回答问题
     * @param query 问题
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @return 回答结果
     */
    @GetMapping(value = "/rag-query", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> ragQuery(
            @RequestParam String query,
            @RequestParam(value = "sessionId", defaultValue =
                    "student_session") String sessionId,
            @RequestParam(value = "userId", defaultValue = "default_userId")
            String userId) {
        return saaService.ragQuery(query, sessionId, userId);
    }

    /**
     * 获取历史对话列表
     * @param userId 用户ID
     * @param type 获取历史对话类型  rag或者chat
     * @return
     */
    @GetMapping(value = "/get-historyList")
    public Result<List<Object>> getHistory(
            @RequestParam(value = "userId") String userId,
            @RequestParam(value = "type") String type){
        return saaService.getHistory(userId, type);
    }

    @GetMapping(value = "/get-history-by-sessionId")
    public Result<Object> getHistoryById(
            @RequestParam(value = "sessionId") String sessionId,
            @RequestParam(value = "userId") String userId,
            @RequestParam(value = "type") String type){
        return saaService.getHistoryBySessionId(sessionId, userId, type);
    }
}
