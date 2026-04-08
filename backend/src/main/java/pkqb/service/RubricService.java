package pkqb.service;

import pkqb.common.Result;
import pkqb.pojo.dto.RubricGenerateRequest;
import pkqb.pojo.dto.RubricGenerateResponse;
import pkqb.pojo.dto.RubricRequest;
import pkqb.pojo.entity.QuestionEntity;
import pkqb.pojo.entity.RubricEntity;

import java.util.List;

public interface RubricService {
    
    /**
     * 添加试卷
     * @param rubricRequest 试卷请求
     * @return 结果
     */
    Result<?> addRubric(RubricRequest rubricRequest);
    
    /**
     * 根据用户ID获取所有Rubric
     * @param userId 用户ID
     * @return 结果
     */
    Result<List<RubricEntity>> getRubricsByUserId(Long userId);
    
    /**
     * 获取所有公开的Rubric（排除当前用户）
     * @param userId 当前用户ID（用于排除自己的）
     * @return 结果
     */
    Result<List<RubricEntity>> getPublicRubrics(Long userId);
    
    /**
     * 根据RubricID获取所有题目
     * @param rubricId RubricID
     * @return 结果
     */
    Result<List<QuestionEntity>> getQuestionsByRubricId(Long rubricId);
    
    /**
     * 修改Rubric (只有创建者可以修改)
     * @param rubricRequest 修改请求
     * @param userId 当前用户ID
     * @return 结果
     */
    Result<?> updateRubric(RubricRequest rubricRequest, Long userId);
    
    /**
     * 删除Rubric (只有创建者可以删除)
     * @param rubricId RubricID
     * @param userId 当前用户ID
     * @return 结果
     */
    Result<?> deleteRubric(Long rubricId, Long userId);
    
    /**
     * 给Rubric添加题目
     * @param questionEntity 题目实体
     * @param userId 当前用户ID
     * @return 结果
     */
    Result<?> addQuestion(QuestionEntity questionEntity, Long userId);
    
    /**
     * 修改Rubric中的题目
     * @param questionEntity 题目实体
     * @param userId 当前用户ID
     * @return 结果
     */
    Result<?> updateQuestion(QuestionEntity questionEntity, Long userId);
    
    /**
     * 删除Rubric中的题目
     * @param questionId 题目ID
     * @param userId 当前用户ID
     * @return 结果
     */
    Result<?> deleteQuestion(Long questionId, Long userId);

    /**
     * 根据Rubric生成HTML文件并保存到MinIO，同时保存记录到file表
     * @param request 生成请求（包含rubricId）
     * @param userId 当前用户ID
     * @return 结果（包含文件信息）
     */
    Result<RubricGenerateResponse> generateHtml(RubricGenerateRequest request, Long userId);
}
