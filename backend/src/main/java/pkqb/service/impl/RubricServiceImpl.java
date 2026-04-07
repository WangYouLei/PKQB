package pkqb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pkqb.common.Result;
import pkqb.mapper.QuestionMapper;
import pkqb.mapper.RubricMapper;
import pkqb.pojo.dto.AiRubric;
import pkqb.pojo.dto.RubricRequest;
import pkqb.pojo.entity.QuestionEntity;
import pkqb.pojo.entity.RubricEntity;
import pkqb.service.RubricService;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class RubricServiceImpl implements RubricService {

    private final RubricMapper rubricMapper;
    private final QuestionMapper questionMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public Result<?> addRubric(RubricRequest rubricRequest) {
        try {
            // 1. 保存试卷
            RubricEntity rubricEntity = new RubricEntity();
            rubricEntity.setTitle(rubricRequest.getTitle());
            rubricEntity.setClassName(rubricRequest.getClassName());
            rubricEntity.setCreateId(rubricRequest.getCreateId());
            rubricEntity.setCreateStudentNo(rubricRequest.getCreateStudentNo());
            rubricEntity.setIsPublic(rubricRequest.getIsPublic());
            rubricEntity.setQuestionCount(rubricRequest.getRubrics() != null ? rubricRequest.getRubrics().size() : 0);
            rubricEntity.setDeleted(0);
            
            rubricMapper.insert(rubricEntity);
            
            Long rubricId = rubricEntity.getId();
            
            // 2. 保存题目
            List<AiRubric> rubrics = rubricRequest.getRubrics();
            if (rubrics != null && !rubrics.isEmpty()) {
                for (int i = 0; i < rubrics.size(); i++) {
                    AiRubric aiRubric = rubrics.get(i);
                    QuestionEntity questionEntity = new QuestionEntity();
                    questionEntity.setRubricId(rubricId);
                    questionEntity.setOrderIndex(i + 1);
                    questionEntity.setQuestionText(aiRubric.getQuestion());
                    questionEntity.setQuestionType(aiRubric.getQuestionType());
                    questionEntity.setAnswer(aiRubric.getAnswer());
                    questionEntity.setExplanation(aiRubric.getExplanation());
                    questionEntity.setDeleted(0);
                    
                    // 数组转JSON
                    try {
                        if (aiRubric.getOptions() != null) {
                            questionEntity.setOptionsJson(objectMapper.writeValueAsString(aiRubric.getOptions()));
                        }
                        if (aiRubric.getCalculationSteps() != null) {
                            questionEntity.setCalculationStepsJson(objectMapper.writeValueAsString(aiRubric.getCalculationSteps()));
                        }
                    } catch (JsonProcessingException e) {
                        log.error("[添加试卷] JSON序列化失败", e);
                    }
                    
                    questionMapper.insert(questionEntity);
                }
            }
            
            return Result.success("试卷添加成功");
            
        } catch (Exception e) {
            log.error("[添加试卷] 添加失败", e);
            return Result.error("添加试卷失败");
        }
    }

    @Override
    public Result<List<RubricEntity>> getRubricsByUserId(Long userId) {
        try {
            LambdaQueryWrapper<RubricEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(RubricEntity::getCreateId, userId)
                   .eq(RubricEntity::getDeleted, 0)
                   .orderByDesc(RubricEntity::getCreateTime);
            List<RubricEntity> rubrics = rubricMapper.selectList(wrapper);
            return Result.success(rubrics);
        } catch (Exception e) {
            log.error("[获取用户试卷] 获取失败", e);
            return Result.error("获取试卷失败");
        }
    }

    @Override
    public Result<List<RubricEntity>> getPublicRubrics(Long excludeUserId) {
        try {
            LambdaQueryWrapper<RubricEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(RubricEntity::getIsPublic, true)
                   .eq(RubricEntity::getDeleted, 0)
                   .ne(RubricEntity::getCreateId, excludeUserId)  // 排除当前用户
                   .orderByDesc(RubricEntity::getCreateTime);
            List<RubricEntity> rubrics = rubricMapper.selectList(wrapper);
            return Result.success(rubrics);
        } catch (Exception e) {
            log.error("[获取公开试卷] 获取失败", e);
            return Result.error("获取公开试卷失败");
        }
    }

    @Override
    public Result<List<QuestionEntity>> getQuestionsByRubricId(Long rubricId) {
        try {
            LambdaQueryWrapper<QuestionEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(QuestionEntity::getRubricId, rubricId)
                   .eq(QuestionEntity::getDeleted, 0)
                   .orderByAsc(QuestionEntity::getOrderIndex);
            List<QuestionEntity> questions = questionMapper.selectList(wrapper);
            return Result.success(questions);
        } catch (Exception e) {
            log.error("[获取题目] 获取失败", e);
            return Result.error("获取题目失败");
        }
    }

    @Override
    @Transactional
    public Result<?> updateRubric(RubricRequest rubricRequest, Long userId) {
        try {
            RubricEntity rubric = rubricMapper.selectById(rubricRequest.getId());
            if (rubric == null) {
                return Result.error("试卷不存在");
            }
            if (!rubric.getCreateId().equals(userId)) {
                return Result.error("只有创建者可以修改");
            }
            
            rubric.setTitle(rubricRequest.getTitle());
            rubric.setClassName(rubricRequest.getClassName());
            rubric.setIsPublic(rubricRequest.getIsPublic());
            rubric.setUpdateTime(LocalDateTime.now());
            rubricMapper.updateById(rubric);
            
            return Result.success("修改成功");
        } catch (Exception e) {
            log.error("[修改试卷] 修改失败", e);
            return Result.error("修改试卷失败");
        }
    }

    @Override
    @Transactional
    public Result<?> deleteRubric(Long rubricId, Long userId) {
        try {
            RubricEntity rubric = rubricMapper.selectById(rubricId);
            if (rubric == null) {
                return Result.error("试卷不存在");
            }
            if (!rubric.getCreateId().equals(userId)) {
                return Result.error("只有创建者可以删除");
            }
            
            // 软删除试卷
            rubric.setDeleted(1);
            rubric.setUpdateTime(LocalDateTime.now());
            rubricMapper.updateById(rubric);
            
            // 软删除试卷下的所有题目
            LambdaQueryWrapper<QuestionEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(QuestionEntity::getRubricId, rubricId)
                   .eq(QuestionEntity::getDeleted, 0);
            List<QuestionEntity> questions = questionMapper.selectList(wrapper);
            for (QuestionEntity question : questions) {
                question.setDeleted(1);
                question.setUpdateTime(LocalDateTime.now());
                questionMapper.updateById(question);
            }
            
            return Result.success("删除成功");
        } catch (Exception e) {
            log.error("[删除试卷] 删除失败", e);
            return Result.error("删除试卷失败");
        }
    }

    @Override
    public Result<?> addQuestion(QuestionEntity questionEntity, Long userId) {
        try {
            RubricEntity rubric = rubricMapper.selectById(questionEntity.getRubricId());
            if (rubric == null) {
                return Result.error("试卷不存在");
            }
            if (!rubric.getCreateId().equals(userId)) {
                return Result.error("只有创建者可以添加题目");
            }
            
            questionEntity.setDeleted(0);
            questionMapper.insert(questionEntity);
            
            // 更新试卷题目数量
            rubric.setQuestionCount(rubric.getQuestionCount() + 1);
            rubric.setUpdateTime(LocalDateTime.now());
            rubricMapper.updateById(rubric);
            
            return Result.success("添加题目成功");
        } catch (Exception e) {
            log.error("[添加题目] 添加失败", e);
            return Result.error("添加题目失败");
        }
    }

    @Override
    public Result<?> updateQuestion(QuestionEntity questionEntity, Long userId) {
        try {
            QuestionEntity question = questionMapper.selectById(questionEntity.getId());
            if (question == null) {
                return Result.error("题目不存在");
            }
            
            RubricEntity rubric = rubricMapper.selectById(question.getRubricId());
            if (rubric == null) {
                return Result.error("试卷不存在");
            }
            if (!rubric.getCreateId().equals(userId)) {
                return Result.error("只有创建者可以修改题目");
            }
            
            question.setQuestionText(questionEntity.getQuestionText());
            question.setQuestionType(questionEntity.getQuestionType());
            question.setOptionsJson(questionEntity.getOptionsJson());
            question.setAnswer(questionEntity.getAnswer());
            question.setExplanation(questionEntity.getExplanation());
            question.setCalculationStepsJson(questionEntity.getCalculationStepsJson());
            question.setUpdateTime(LocalDateTime.now());
            questionMapper.updateById(question);
            
            return Result.success("修改题目成功");
        } catch (Exception e) {
            log.error("[修改题目] 修改失败", e);
            return Result.error("修改题目失败");
        }
    }

    @Override
    public Result<?> deleteQuestion(Long questionId, Long userId) {
        try {
            QuestionEntity question = questionMapper.selectById(questionId);
            if (question == null) {
                return Result.error("题目不存在");
            }
            
            RubricEntity rubric = rubricMapper.selectById(question.getRubricId());
            if (rubric == null) {
                return Result.error("试卷不存在");
            }
            if (!rubric.getCreateId().equals(userId)) {
                return Result.error("只有创建者可以删除题目");
            }
            
            // 软删除题目
            question.setDeleted(1);
            question.setUpdateTime(LocalDateTime.now());
            questionMapper.updateById(question);
            
            // 更新试卷题目数量
            rubric.setQuestionCount(Math.max(0, rubric.getQuestionCount() - 1));
            rubric.setUpdateTime(LocalDateTime.now());
            rubricMapper.updateById(rubric);
            
            return Result.success("删除题目成功");
        } catch (Exception e) {
            log.error("[删除题目] 删除失败", e);
            return Result.error("删除题目失败");
        }
    }
}
