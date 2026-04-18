package pkqb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pkqb.common.Result;
import pkqb.pojo.dto.RubricGenerateRequest;
import pkqb.pojo.dto.RubricGenerateResponse;
import pkqb.pojo.dto.RubricRequest;
import pkqb.pojo.entity.QuestionEntity;
import pkqb.pojo.entity.RubricEntity;
import pkqb.service.RubricService;

import java.util.List;

@RestController
@RequestMapping("/api/rubric")
@RequiredArgsConstructor
@Tag(name = "题库管理", description = "试卷、题目管理接口")
public class RubricController {
    
    private final RubricService rubricService;
    
    /**
     * 添加试卷
     * @param rubricRequest 试卷请求
     * @return 添加结果
     */
    @PostMapping(value = "/add-rubric")
    @Operation(summary = "添加试卷", description = "创建新的试卷")
    public Result<?> addRubric(@Valid @RequestBody RubricRequest rubricRequest) {
        return rubricService.addRubric(rubricRequest);
    }
    
    /**
     * 根据用户ID获取所有Rubric
     * @param request HttpServletRequest
     * @return 结果
     */
    @GetMapping("/my")
    @Operation(summary = "获取我的试卷", description = "获取当前用户创建的所有试卷")
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
    @Operation(summary = "获取公开试卷", description = "获取班级公开的试卷列表")
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
    @Operation(summary = "获取试卷题目", description = "获取指定试卷的所有题目")
    public Result<List<QuestionEntity>> getQuestionsByRubricId(
            @Parameter(description = "试卷ID") @PathVariable Long rubricId) {
        return rubricService.getQuestionsByRubricId(rubricId);
    }
    
    /**
     * 修改Rubric (只有创建者可以修改)
     * @param rubricRequest 修改请求
     * @param request HttpServletRequest
     * @return 结果
     */
    @PutMapping("/update")
    @Operation(summary = "修改试卷", description = "修改试卷信息（仅创建者可修改）")
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
    @Operation(summary = "删除试卷", description = "删除试卷（仅创建者可删除）")
    public Result<?> deleteRubric(
            @Parameter(description = "试卷ID") @PathVariable Long rubricId, 
            HttpServletRequest request) {
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
    @Operation(summary = "添加题目", description = "向试卷添加题目")
    public Result<?> addQuestion(
            @Parameter(description = "试卷ID") @PathVariable Long rubricId,
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
    @Operation(summary = "修改题目", description = "修改试卷中的题目")
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
    @Operation(summary = "删除题目", description = "删除试卷中的题目")
    public Result<?> deleteQuestion(
            @Parameter(description = "题目ID") @PathVariable Long questionId, 
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return rubricService.deleteQuestion(questionId, userId);
    }

    /**
     * 根据Rubric生成HTML文件
     * @param request 生成请求
     * @param httpRequest HttpServletRequest
     * @return 结果
     */
    @PostMapping("/generate-html")
    @Operation(summary = "生成HTML试卷", description = "将试卷生成HTML格式")
    public Result<RubricGenerateResponse> generateHtml(
            @Valid @RequestBody RubricGenerateRequest request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        return rubricService.generateHtml(request, userId);
    }
}
