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
    
    @PostMapping(value = "/add-rubric")
    @Operation(summary = "添加试卷", description = "创建新的试卷")
    public Result<?> addRubric(@Valid @RequestBody RubricRequest rubricRequest) {
        return rubricService.addRubric(rubricRequest);
    }
    
    @GetMapping("/my")
    @Operation(summary = "获取我的试卷", description = "获取当前用户创建的所有试卷")
    public Result<List<RubricEntity>> getMyRubrics(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return rubricService.getRubricsByUserId(userId);
    }
    
    @GetMapping("/public")
    @Operation(summary = "获取公开试卷", description = "获取班级公开的试卷列表")
    public Result<List<RubricEntity>> getPublicRubrics(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return rubricService.getPublicRubrics(userId);
    }
    
    @GetMapping("/{rubricId}/questions")
    @Operation(summary = "获取试卷题目", description = "获取指定试卷的所有题目")
    public Result<List<QuestionEntity>> getQuestionsByRubricId(
            @Parameter(description = "试卷ID") @PathVariable Long rubricId) {
        return rubricService.getQuestionsByRubricId(rubricId);
    }
    
    @PutMapping("/update")
    @Operation(summary = "修改试卷", description = "修改试卷信息（仅创建者可修改）")
    public Result<?> updateRubric(@RequestBody RubricRequest rubricRequest, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return rubricService.updateRubric(rubricRequest, userId);
    }
    
    @DeleteMapping("/{rubricId}")
    @Operation(summary = "删除试卷", description = "删除试卷（仅创建者可删除）")
    public Result<?> deleteRubric(
            @Parameter(description = "试卷ID") @PathVariable Long rubricId, 
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return rubricService.deleteRubric(rubricId, userId);
    }
    
    @PutMapping("/question/update")
    @Operation(summary = "修改题目", description = "修改试卷中的题目")
    public Result<?> updateQuestion(@RequestBody QuestionEntity questionEntity, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return rubricService.updateQuestion(questionEntity, userId);
    }
    
    @PostMapping("/{rubricId}/questions/batch")
    @Operation(summary = "批量保存题目", description = "批量保存试卷题目（先删除再添加）")
    public Result<?> batchSaveQuestions(
            @Parameter(description = "试卷ID") @PathVariable Long rubricId,
            @RequestBody List<QuestionEntity> questions,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return rubricService.batchSaveQuestions(rubricId, questions, userId);
    }

    @PostMapping("/generate-html")
    @Operation(summary = "生成HTML试卷", description = "将试卷生成HTML格式")
    public Result<RubricGenerateResponse> generateHtml(
            @Valid @RequestBody RubricGenerateRequest request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        return rubricService.generateHtml(request, userId);
    }
}
