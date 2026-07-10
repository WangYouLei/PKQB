package pkqb.service;

import pkqb.common.Result;
import pkqb.pojo.dto.ReviewResultRequest;
import pkqb.pojo.dto.WrongQuestionRequest;
import pkqb.pojo.entity.WrongQuestionEntity;

import java.util.List;

/**
 * 错题本服务接口
 */
public interface WrongQuestionService {

    /**
     * 添加错题（做题时答错自动添加）
     * @param request 错题请求
     * @param userId 用户ID
     * @return 结果
     */
    Result<?> addWrongQuestion(WrongQuestionRequest request, Long userId);

    /**
     * 获取用户所有错题
     * @param userId 用户ID
     * @return 错题列表
     */
    Result<List<WrongQuestionEntity>> getWrongQuestions(Long userId);

    /**
     * 获取今日待复习的错题
     * @param userId 用户ID
     * @return 待复习错题列表
     */
    Result<List<WrongQuestionEntity>> getTodayReviewQuestions(Long userId);

    /**
     * 提交复习结果，更新 SM-2 间隔复习参数
     * @param request 复习结果请求
     * @param userId 用户ID
     * @return 结果
     */
    Result<?> submitReviewResult(ReviewResultRequest request, Long userId);

    /**
     * 删除错题记录
     * @param wrongQuestionId 错题ID
     * @param userId 用户ID
     * @return 结果
     */
    Result<?> deleteWrongQuestion(Long wrongQuestionId, Long userId);

    /**
     * 批量删除错题记录
     * @param ids 错题ID列表
     * @param userId 用户ID
     * @return 结果
     */
    Result<?> batchDeleteWrongQuestions(List<Long> ids, Long userId);

    /**
     * 获取错题统计信息
     * @param userId 用户ID
     * @return 统计信息
     */
    Result<?> getWrongQuestionStats(Long userId);
}
