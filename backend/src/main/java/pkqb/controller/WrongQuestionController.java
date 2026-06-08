package pkqb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pkqb.common.Result;
import pkqb.pojo.dto.ReviewResultRequest;
import pkqb.pojo.dto.WrongQuestionRequest;
import pkqb.pojo.entity.WrongQuestionEntity;
import pkqb.service.WrongQuestionService;

import java.util.List;

/**
 * 错题本控制器
 */
@RestController
@RequestMapping("/api/wrong-question")
@RequiredArgsConstructor
@Tag(name = "错题本", description = "错题管理和间隔复习接口")
public class WrongQuestionController {

    private final WrongQuestionService wrongQuestionService;

    @PostMapping
    @Operation(summary = "添加错题", description = "做题答错时自动添加到错题本")
    public Result<?> addWrongQuestion(@Valid @RequestBody WrongQuestionRequest request,
                                      HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        return wrongQuestionService.addWrongQuestion(request, userId);
    }

    @GetMapping("/list")
    @Operation(summary = "获取错题列表", description = "获取当前用户的所有错题")
    public Result<List<WrongQuestionEntity>> getWrongQuestions(HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        return wrongQuestionService.getWrongQuestions(userId);
    }

    @GetMapping("/today-review")
    @Operation(summary = "获取今日待复习错题", description = "获取今天需要复习的错题列表")
    public Result<List<WrongQuestionEntity>> getTodayReviewQuestions(HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        return wrongQuestionService.getTodayReviewQuestions(userId);
    }

    @PostMapping("/review")
    @Operation(summary = "提交复习结果", description = "复习后提交答对/答错，更新间隔复习参数")
    public Result<?> submitReviewResult(@Valid @RequestBody ReviewResultRequest request,
                                        HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        return wrongQuestionService.submitReviewResult(request, userId);
    }

    @DeleteMapping("/{wrongQuestionId}")
    @Operation(summary = "删除错题", description = "从错题本中移除指定错题")
    public Result<?> deleteWrongQuestion(@PathVariable Long wrongQuestionId,
                                         HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        return wrongQuestionService.deleteWrongQuestion(wrongQuestionId, userId);
    }

    @DeleteMapping("/batch")
    @Operation(summary = "批量删除错题", description = "批量从错题本中移除指定错题")
    public Result<?> batchDeleteWrongQuestions(@RequestBody List<Long> ids,
                                               HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        return wrongQuestionService.batchDeleteWrongQuestions(ids, userId);
    }

    @GetMapping("/stats")
    @Operation(summary = "获取错题统计", description = "获取错题总数、今日待复习数、已掌握数等统计")
    public Result<?> getWrongQuestionStats(HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或登录已过期");
        }
        return wrongQuestionService.getWrongQuestionStats(userId);
    }
}
