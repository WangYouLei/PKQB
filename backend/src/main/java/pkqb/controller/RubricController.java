package pkqb.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pkqb.common.Result;
import pkqb.pojo.dto.RubricRequest;
import pkqb.pojo.entity.QuestionEntity;
import pkqb.pojo.entity.RubricEntity;
import pkqb.service.RubricService;

import java.util.List;

@RestController
@RequestMapping("/api/rubric")
@RequiredArgsConstructor
public class RubricController {
    
    private final RubricService rubricService;
    
    /**
     * 添加试卷
     * @param rubricRequest 试卷请求
     * @return 添加结果
     */
    @PostMapping(value = "/add-rubric")
    public Result<?> addRubric(@RequestBody RubricRequest rubricRequest) {
        return rubricService.addRubric(rubricRequest);
    }
    
    /**
     * 根据用户ID获取所有Rubric
     * @param request HttpServletRequest
     * @return 结果
     */
    @GetMapping("/my")
    public Result<List<RubricEntity>> getMyRubrics(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return rubricService.getRubricsByUserId(userId);
    }
    
    /**
     * 获取所有公开的Rubric（班级公开，排除当前用户）
     * @param request HttpServletRequest
     * @return 结果
     */
    @GetMapping("/public")
    public Result<List<RubricEntity>> getPublicRubrics(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return rubricService.getPublicRubrics(userId);
    }
    
    /**
     * 根据Rubric获取所有题目
     * @param rubricId RubricID
     * @return 结果
     */
    @GetMapping("/{rubricId}/questions")
    public Result<List<QuestionEntity>> getQuestionsByRubricId(@PathVariable Long rubricId) {
        return rubricService.getQuestionsByRubricId(rubricId);
    }
    
    /**
     * 修改Rubric (只有创建者可以修改)
     * @param rubricRequest 修改请求
     * @param request HttpServletRequest
     * @return 结果
     */
    @PutMapping("/update")
    public Result<?> updateRubric(@RequestBody RubricRequest rubricRequest, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return rubricService.updateRubric(rubricRequest, userId);
    }
    
    /**
     * 删除Rubric (只有创建者可以删除)
     * @param rubricId RubricID
     * @param request HttpServletRequest
     * @return 结果
     */
    @DeleteMapping("/{rubricId}")
    public Result<?> deleteRubric(@PathVariable Long rubricId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return rubricService.deleteRubric(rubricId, userId);
    }
    
    /**
     * 给Rubric添加题目
     * @param questionEntity 题目实体
     * @param request HttpServletRequest
     * @return 结果
     */
    @PostMapping("/{rubricId}/question")
    public Result<?> addQuestion(
            @PathVariable Long rubricId,
            @RequestBody QuestionEntity questionEntity,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        questionEntity.setRubricId(rubricId);
        return rubricService.addQuestion(questionEntity, userId);
    }
    
    /**
     * 修改Rubric中的题目
     * @param questionEntity 题目实体
     * @param request HttpServletRequest
     * @return 结果
     */
    @PutMapping("/question/update")
    public Result<?> updateQuestion(@RequestBody QuestionEntity questionEntity, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return rubricService.updateQuestion(questionEntity, userId);
    }
    
    /**
     * 删除Rubric中的题目
     * @param questionId 题目ID
     * @param request HttpServletRequest
     * @return 结果
     */
    @DeleteMapping("/question/{questionId}")
    public Result<?> deleteQuestion(@PathVariable Long questionId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return rubricService.deleteQuestion(questionId, userId);
    }
}
