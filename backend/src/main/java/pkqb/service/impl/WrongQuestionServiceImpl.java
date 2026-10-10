package pkqb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import pkqb.common.Result;
import pkqb.mapper.QuestionMapper;
import pkqb.mapper.RubricMapper;
import pkqb.mapper.WrongQuestionMapper;
import pkqb.pojo.dto.ReviewResultRequest;
import pkqb.pojo.dto.WrongQuestionRequest;
import pkqb.pojo.entity.QuestionEntity;
import pkqb.pojo.entity.QuestionResourceEntity;
import pkqb.pojo.entity.RubricEntity;
import pkqb.pojo.entity.WrongQuestionEntity;
import pkqb.service.QuestionResourceService;
import pkqb.service.WrongQuestionService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 错题本服务实现
 * 包含 SM-2 间隔复习算法
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WrongQuestionServiceImpl implements WrongQuestionService {

    private final WrongQuestionMapper wrongQuestionMapper;
    private final QuestionMapper questionMapper;
    private final RubricMapper rubricMapper;
    private final QuestionResourceService questionResourceService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> addWrongQuestion(WrongQuestionRequest request, Long userId) {
        // 检查是否已存在该错题
        LambdaQueryWrapper<WrongQuestionEntity> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(WrongQuestionEntity::getUserId, userId)
                    .eq(WrongQuestionEntity::getQuestionId, request.getQuestionId())
                    .last("LIMIT 1");
        List<WrongQuestionEntity> existingList = wrongQuestionMapper.selectList(existWrapper);
        WrongQuestionEntity existing = existingList.isEmpty() ? null : existingList.get(0);

        if (existing != null) {
            // 已存在，增加错误次数，重置复习参数
            existing.setWrongCount(existing.getWrongCount() + 1);
            existing.setUserAnswer(HtmlUtils.htmlEscape(request.getUserAnswer()));
            // 答错后降低易度因子，缩短间隔
            existing.setEaseFactor(Math.max(1.3, existing.getEaseFactor() - 0.2));
            existing.setIntervalDays(1);
            existing.setNextReviewDate(LocalDate.now().plusDays(1));
            existing.setMasteryLevel(0);
            existing.setUpdateTime(LocalDateTime.now());
            wrongQuestionMapper.updateById(existing);
            log.info("[添加错题] 更新已有错题, userId={}, questionId={}, wrongCount={}",
                    userId, request.getQuestionId(), existing.getWrongCount());
            return Result.success("错题记录已更新");
        }

        // 获取原题目信息
        QuestionEntity question = questionMapper.selectById(request.getQuestionId());
        if (question == null) {
            throw new IllegalArgumentException("题目不存在");
        }

        // 创建新错题记录
        WrongQuestionEntity entity = new WrongQuestionEntity();
        entity.setUserId(userId);
        entity.setQuestionId(request.getQuestionId());
        entity.setRubricId(request.getRubricId());
        entity.setQuestionText(question.getQuestionText());
        entity.setQuestionType(question.getQuestionType());
        entity.setOptionsJson(question.getOptionsJson());
        entity.setAnswer(question.getAnswer());
        entity.setExplanation(question.getExplanation());
        entity.setCalculationStepsJson(question.getCalculationStepsJson());
        entity.setUserAnswer(HtmlUtils.htmlEscape(request.getUserAnswer()));
        entity.setWrongCount(1);
        entity.setCorrectCount(0);
        entity.setEaseFactor(2.5);  // SM-2 默认易度因子
        entity.setIntervalDays(1);  // 初始间隔1天
        entity.setNextReviewDate(LocalDate.now().plusDays(1));  // 明天复习
        entity.setMasteryLevel(0);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());

        wrongQuestionMapper.insert(entity);
        log.info("[添加错题] 新增错题, userId={}, questionId={}", userId, request.getQuestionId());
        return Result.success("已添加到错题本");
    }

    @Override
    public Result<List<WrongQuestionEntity>> getWrongQuestions(Long userId) {
        try {
            LambdaQueryWrapper<WrongQuestionEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(WrongQuestionEntity::getUserId, userId)
                   .orderByDesc(WrongQuestionEntity::getCreateTime);
            List<WrongQuestionEntity> list = wrongQuestionMapper.selectList(wrapper);

            // 批量获取试卷标题
            List<Long> rubricIds = list.stream()
                    .map(WrongQuestionEntity::getRubricId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            Map<Long, String> rubricTitleMap = Map.of();
            if (!rubricIds.isEmpty()) {
                List<RubricEntity> rubrics = rubricMapper.selectBatchIds(rubricIds);
                rubricTitleMap = rubrics.stream()
                        .collect(Collectors.toMap(RubricEntity::getId, RubricEntity::getTitle, (a, b) -> a));
            }

            // 批量获取题目资源
            List<Long> questionIds = list.stream()
                    .map(WrongQuestionEntity::getQuestionId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            Map<Long, List<QuestionResourceEntity>> resourceMap = Map.of();
            if (!questionIds.isEmpty()) {
                resourceMap = questionResourceService.getByQuestionIds(questionIds);
            }

            for (WrongQuestionEntity wq : list) {
                if (wq.getRubricId() != null) {
                    wq.setRubricTitle(rubricTitleMap.get(wq.getRubricId()));
                }
                if (wq.getQuestionId() != null) {
                    wq.setResources(resourceMap.getOrDefault(wq.getQuestionId(), List.of()));
                }
            }

            return Result.success(list);
        } catch (Exception e) {
            log.error("[获取错题] 获取失败", e);
            return Result.error("获取错题列表失败");
        }
    }

    @Override
    public Result<List<WrongQuestionEntity>> getTodayReviewQuestions(Long userId) {
        try {
            LocalDate today = LocalDate.now();
            LambdaQueryWrapper<WrongQuestionEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(WrongQuestionEntity::getUserId, userId)
                   .le(WrongQuestionEntity::getNextReviewDate, today)  // 复习日期 <= 今天
                   .lt(WrongQuestionEntity::getMasteryLevel, 3)       // 未完全掌握
                   .orderByAsc(WrongQuestionEntity::getNextReviewDate);
            List<WrongQuestionEntity> list = wrongQuestionMapper.selectList(wrapper);

            // 批量获取试卷标题
            List<Long> rubricIds = list.stream()
                    .map(WrongQuestionEntity::getRubricId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            Map<Long, String> rubricTitleMap = Map.of();
            if (!rubricIds.isEmpty()) {
                List<RubricEntity> rubrics = rubricMapper.selectBatchIds(rubricIds);
                rubricTitleMap = rubrics.stream()
                        .collect(Collectors.toMap(RubricEntity::getId, RubricEntity::getTitle, (a, b) -> a));
            }

            // 批量获取题目资源
            List<Long> questionIds = list.stream()
                    .map(WrongQuestionEntity::getQuestionId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            Map<Long, List<QuestionResourceEntity>> resourceMap = Map.of();
            if (!questionIds.isEmpty()) {
                resourceMap = questionResourceService.getByQuestionIds(questionIds);
            }

            for (WrongQuestionEntity wq : list) {
                if (wq.getRubricId() != null) {
                    wq.setRubricTitle(rubricTitleMap.get(wq.getRubricId()));
                }
                if (wq.getQuestionId() != null) {
                    wq.setResources(resourceMap.getOrDefault(wq.getQuestionId(), List.of()));
                }
            }

            return Result.success(list);
        } catch (Exception e) {
            log.error("[获取今日复习] 获取失败", e);
            return Result.error("获取复习题目失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> submitReviewResult(ReviewResultRequest request, Long userId) {
        WrongQuestionEntity wq = wrongQuestionMapper.selectById(request.getWrongQuestionId());
        if (wq == null) {
            throw new IllegalArgumentException("错题记录不存在");
        }
        if (!wq.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作");
        }

        boolean correct = request.getCorrect();
        wq.setLastReviewTime(LocalDateTime.now());

        if (correct) {
            // 答对：增加正确次数，使用 SM-2 算法计算下次复习时间
            wq.setCorrectCount(wq.getCorrectCount() + 1);

            // SM-2 算法简化版
            int quality = 4;  // 答对默认质量4（0-5分制，4=答对但有犹豫，5=完美答对）
            double ef = wq.getEaseFactor();
            ef = ef + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02));
            ef = Math.max(1.3, ef);  // 易度因子最低1.3
            wq.setEaseFactor(ef);

            int interval = wq.getIntervalDays();
            if (wq.getCorrectCount() == 1) {
                interval = 1;  // 第一次答对，1天后复习
            } else if (wq.getCorrectCount() == 2) {
                interval = 3;  // 第二次答对，3天后复习
            } else {
                // 之后按易度因子递增间隔
                interval = (int) Math.ceil(interval * ef);
            }
            wq.setIntervalDays(interval);
            wq.setNextReviewDate(LocalDate.now().plusDays(interval));

            // 更新掌握程度
            if (wq.getCorrectCount() >= 5 && ef >= 2.0) {
                wq.setMasteryLevel(3);  // 完全掌握
            } else if (wq.getCorrectCount() >= 3) {
                wq.setMasteryLevel(2);  // 基本掌握
            } else {
                wq.setMasteryLevel(1);  // 初步掌握
            }
        } else {
            // 答错：重置复习参数
            wq.setWrongCount(wq.getWrongCount() + 1);
            wq.setEaseFactor(Math.max(1.3, wq.getEaseFactor() - 0.2));
            wq.setIntervalDays(1);
            wq.setNextReviewDate(LocalDate.now().plusDays(1));
            wq.setMasteryLevel(0);  // 重新归为未掌握
        }

        wq.setUpdateTime(LocalDateTime.now());
        wrongQuestionMapper.updateById(wq);

        log.info("[复习结果] userId={}, wrongQuestionId={}, correct={}, nextReview={}, mastery={}",
                userId, request.getWrongQuestionId(), correct, wq.getNextReviewDate(), wq.getMasteryLevel());
        return Result.success(correct ? "答对了！继续保持" : "答错了，已重新安排复习");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> deleteWrongQuestion(Long wrongQuestionId, Long userId) {
        WrongQuestionEntity wq = wrongQuestionMapper.selectById(wrongQuestionId);
        if (wq == null) {
            throw new IllegalArgumentException("错题记录不存在");
        }
        if (!wq.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权删除");
        }
        wrongQuestionMapper.deleteById(wrongQuestionId);
        log.info("[删除错题] userId={}, wrongQuestionId={}", userId, wrongQuestionId);
        return Result.success("删除成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> batchDeleteWrongQuestions(List<Long> ids, Long userId) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("请选择要删除的错题");
        }
        LambdaQueryWrapper<WrongQuestionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WrongQuestionEntity::getUserId, userId)
               .in(WrongQuestionEntity::getId, ids);
        int deleted = wrongQuestionMapper.delete(wrapper);
        log.info("[批量删除错题] userId={}, count={}", userId, deleted);
        return Result.success("成功删除" + deleted + "条错题");
    }

    @Override
    public Result<?> getWrongQuestionStats(Long userId) {
        try {
            LambdaQueryWrapper<WrongQuestionEntity> allWrapper = new LambdaQueryWrapper<>();
            allWrapper.eq(WrongQuestionEntity::getUserId, userId);
            long totalCount = wrongQuestionMapper.selectCount(allWrapper);

            LocalDate today = LocalDate.now();
            LambdaQueryWrapper<WrongQuestionEntity> todayWrapper = new LambdaQueryWrapper<>();
            todayWrapper.eq(WrongQuestionEntity::getUserId, userId)
                        .le(WrongQuestionEntity::getNextReviewDate, today)
                        .lt(WrongQuestionEntity::getMasteryLevel, 3);
            long todayReviewCount = wrongQuestionMapper.selectCount(todayWrapper);

            LambdaQueryWrapper<WrongQuestionEntity> masteredWrapper = new LambdaQueryWrapper<>();
            masteredWrapper.eq(WrongQuestionEntity::getUserId, userId)
                           .eq(WrongQuestionEntity::getMasteryLevel, 3);
            long masteredCount = wrongQuestionMapper.selectCount(masteredWrapper);

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalCount", totalCount);
            stats.put("todayReviewCount", todayReviewCount);
            stats.put("masteredCount", masteredCount);
            stats.put("learningCount", totalCount - masteredCount);

            return Result.success(stats);
        } catch (Exception e) {
            log.error("[错题统计] 获取失败", e);
            return Result.error("获取统计信息失败");
        }
    }
}
