package pkqb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pkqb.common.Result;
import pkqb.mapper.UserMapper;
import pkqb.pojo.dto.RubricGenerateRequest;
import pkqb.pojo.dto.RubricGenerateResponse;
import pkqb.pojo.dto.RubricRequest;
import pkqb.pojo.entity.QuestionEntity;
import pkqb.pojo.entity.RubricEntity;
import pkqb.service.OssService;
import pkqb.service.RubricService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/rubric")
@RequiredArgsConstructor
@Tag(name = "题库管理", description = "试卷、题目管理接口")
public class RubricController {
    
    private final RubricService rubricService;
    private final UserMapper userMapper;
    private final OssService ossService;
    
    @PostMapping(value = "/add-rubric")
    @Operation(summary = "添加试卷", description = "创建新的试卷")
    public Result<?> addRubric(@Valid @RequestBody RubricRequest rubricRequest, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        rubricRequest.setCreateId(userId);
        // 从用户信息获取学号，防止客户端伪造
        pkqb.pojo.entity.UserEntity user = userMapper.selectById(userId);
        if (user != null) {
            rubricRequest.setCreateStudentNo(user.getStudentNo());
        }
        return rubricService.addRubric(rubricRequest);
    }
    
    @GetMapping("/my")
    @Operation(summary = "获取我的试卷", description = "获取当前用户创建的所有试卷")
    public Result<List<RubricEntity>> getMyRubrics(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        return rubricService.getRubricsByUserId(userId);
    }
    
    @GetMapping("/public")
    @Operation(summary = "获取公开试卷", description = "获取班级公开的试卷列表")
    public Result<List<RubricEntity>> getPublicRubrics(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        return rubricService.getPublicRubrics(userId);
    }
    
    @GetMapping("/{rubricId}/questions")
    @Operation(summary = "获取试卷题目", description = "获取指定试卷的所有题目")
    public Result<List<QuestionEntity>> getQuestionsByRubricId(
            @Parameter(description = "试卷ID") @PathVariable Long rubricId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        return rubricService.getQuestionsByRubricId(rubricId, userId);
    }
    
    @PutMapping("/update")
    @Operation(summary = "修改试卷", description = "修改试卷信息（仅创建者可修改）")
    public Result<?> updateRubric(@Valid @RequestBody RubricRequest rubricRequest, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        return rubricService.updateRubric(rubricRequest, userId);
    }
    
    @DeleteMapping("/{rubricId}")
    @Operation(summary = "删除试卷", description = "删除试卷（仅创建者可删除）")
    public Result<?> deleteRubric(
            @Parameter(description = "试卷ID") @PathVariable Long rubricId, 
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        return rubricService.deleteRubric(rubricId, userId);
    }
    
    @PutMapping("/question/update")
    @Operation(summary = "修改题目", description = "修改试卷中的题目")
    public Result<?> updateQuestion(@Valid @RequestBody QuestionEntity questionEntity, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        return rubricService.updateQuestion(questionEntity, userId);
    }
    
    @PostMapping("/{rubricId}/questions/batch")
    @Operation(summary = "批量保存题目", description = "批量保存试卷题目（先删除再添加）")
    public Result<?> batchSaveQuestions(
            @Parameter(description = "试卷ID") @PathVariable Long rubricId,
            @Valid @RequestBody List<QuestionEntity> questions,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        if (questions == null || questions.isEmpty()) {
            return Result.error(400, "题目列表不能为空");
        }
        if (questions.size() > 100) {
            return Result.error(400, "题目数量不能超过100个");
        }
        return rubricService.batchSaveQuestions(rubricId, questions, userId);
    }

    @PostMapping("/generate-html")
    @Operation(summary = "生成HTML试卷", description = "将试卷生成HTML格式")
    public Result<RubricGenerateResponse> generateHtml(
            @Valid @RequestBody RubricGenerateRequest request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        return rubricService.generateHtml(request, userId);
    }

    @DeleteMapping("/batch")
    @Operation(summary = "批量删除试卷", description = "批量删除多个试卷（仅创建者可删除）")
    public Result<?> batchDeleteRubrics(@RequestBody List<Long> rubricIds, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        if (rubricIds == null || rubricIds.size() > 100) {
            return Result.error("批量操作数量不能超过100个");
        }
        return rubricService.batchDeleteRubrics(rubricIds, userId);
    }

    @PostMapping("/upload-image")
    @Operation(summary = "上传题目资源图片", description = "上传图片用于题目选项、答案等，返回图片URL")
    public Result<Map<String, String>> uploadResourceImage(
            @Parameter(description = "图片文件") @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        if (file.isEmpty()) {
            return Result.error("文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return Result.error("文件名不能为空");
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        java.util.Set<String> allowedExtensions = java.util.Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp");
        if (!allowedExtensions.contains(extension)) {
            return Result.error("不支持的图片类型: " + extension);
        }
        try {
            String objectKey = "question-image/" + userId + "/" + UUID.randomUUID() + "." + extension;
            String contentType = file.getContentType() != null ? file.getContentType() : "image/" + extension;
            String url = ossService.upload(objectKey, file.getBytes(), contentType);
            return Result.success("上传成功", Map.of("url", url));
        } catch (Exception e) {
            return Result.error("上传失败: " + e.getMessage());
        }
    }
}
