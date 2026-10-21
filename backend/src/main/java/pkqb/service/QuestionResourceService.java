package pkqb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pkqb.mapper.QuestionResourceMapper;
import pkqb.pojo.entity.QuestionResourceEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionResourceService {

    private final QuestionResourceMapper questionResourceMapper;

    public void saveResource(Long questionId, String type, String label, String url, String mimeType, int sortOrder) {
        QuestionResourceEntity entity = new QuestionResourceEntity();
        entity.setQuestionId(questionId);
        entity.setType(type);
        entity.setLabel(label);
        entity.setUrl(url);
        entity.setMimeType(mimeType);
        entity.setSortOrder(sortOrder);
        entity.setDeleted(0);
        questionResourceMapper.insert(entity);
    }

    public List<QuestionResourceEntity> getByQuestionId(Long questionId) {
        LambdaQueryWrapper<QuestionResourceEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionResourceEntity::getQuestionId, questionId)
               .eq(QuestionResourceEntity::getDeleted, 0)
               .orderByAsc(QuestionResourceEntity::getType)
               .orderByAsc(QuestionResourceEntity::getSortOrder);
        return questionResourceMapper.selectList(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void softDeleteByQuestionId(Long questionId) {
        if (questionId == null) return;
        LambdaUpdateWrapper<QuestionResourceEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(QuestionResourceEntity::getQuestionId, questionId)
               .set(QuestionResourceEntity::getDeleted, 1)
               .set(QuestionResourceEntity::getUpdateTime, LocalDateTime.now());
        questionResourceMapper.update(null, wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void softDeleteByQuestionIds(List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) return;
        LambdaUpdateWrapper<QuestionResourceEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(QuestionResourceEntity::getQuestionId, questionIds)
               .set(QuestionResourceEntity::getDeleted, 1)
               .set(QuestionResourceEntity::getUpdateTime, LocalDateTime.now());
        questionResourceMapper.update(null, wrapper);
    }

    public void deleteByQuestionId(Long questionId) {
        LambdaQueryWrapper<QuestionResourceEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionResourceEntity::getQuestionId, questionId);
        questionResourceMapper.delete(wrapper);
    }

    public Map<Long, List<QuestionResourceEntity>> getByQuestionIds(List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<QuestionResourceEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(QuestionResourceEntity::getQuestionId, questionIds)
               .eq(QuestionResourceEntity::getDeleted, 0)
               .orderByAsc(QuestionResourceEntity::getType)
               .orderByAsc(QuestionResourceEntity::getSortOrder);
        List<QuestionResourceEntity> resources = questionResourceMapper.selectList(wrapper);
        return resources.stream()
                .collect(Collectors.groupingBy(QuestionResourceEntity::getQuestionId));
    }

    /**
     * 按题目ID批量查询资源（含已软删的）。
     * 错题本场景：删除试卷会软删题目和资源记录，但错题记录及其引用的图片文件仍在，
     * 需返回含软删的资源，否则错题本图片全部消失。
     */
    public Map<Long, List<QuestionResourceEntity>> getAllByQuestionIds(List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<QuestionResourceEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(QuestionResourceEntity::getQuestionId, questionIds)
               .orderByAsc(QuestionResourceEntity::getType)
               .orderByAsc(QuestionResourceEntity::getSortOrder);
        List<QuestionResourceEntity> resources = questionResourceMapper.selectList(wrapper);
        return resources.stream()
                .collect(Collectors.groupingBy(QuestionResourceEntity::getQuestionId));
    }
}
