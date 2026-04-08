package pkqb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pkqb.common.Result;
import pkqb.mapper.FileMapper;
import pkqb.mapper.QuestionMapper;
import pkqb.mapper.RubricMapper;
import pkqb.pojo.dto.AiRubric;
import pkqb.pojo.dto.RubricGenerateRequest;
import pkqb.pojo.dto.RubricGenerateResponse;
import pkqb.pojo.dto.RubricRequest;
import pkqb.pojo.entity.FileEntity;
import pkqb.pojo.entity.QuestionEntity;
import pkqb.pojo.entity.RubricEntity;
import pkqb.service.MinioService;
import pkqb.service.RubricService;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class RubricServiceImpl implements RubricService {

    private final RubricMapper rubricMapper;
    private final QuestionMapper questionMapper;
    private final FileMapper fileMapper;
    private final MinioService minioService;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<RubricGenerateResponse> generateHtml(RubricGenerateRequest request, Long userId) {
        try {
            // 1. 获取Rubric
            RubricEntity rubric = rubricMapper.selectById(request.getRubricId());
            if (rubric == null) {
                return Result.error("试卷不存在");
            }

            // 2. 获取所有题目
            LambdaQueryWrapper<QuestionEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(QuestionEntity::getRubricId, request.getRubricId())
                   .eq(QuestionEntity::getDeleted, 0)
                   .orderByAsc(QuestionEntity::getOrderIndex);
            List<QuestionEntity> questions = questionMapper.selectList(wrapper);

            // 3. 生成HTML内容
            String htmlContent = generateHtmlContent(rubric, questions);

            // 4. 上传到MinIO
            String fileName = request.getFileName() != null && !request.getFileName().isEmpty()
                    ? request.getFileName()
                    : rubric.getTitle() + ".html";
            String objectKey = "rubric/" + userId + "/" + UUID.randomUUID() + ".html";

            ByteArrayInputStream inputStream = new ByteArrayInputStream(htmlContent.getBytes(StandardCharsets.UTF_8));
            minioService.upload(objectKey, inputStream, "text/html", htmlContent.getBytes(StandardCharsets.UTF_8).length);

            // 5. 保存到数据库
            FileEntity fileEntity = new FileEntity();
            fileEntity.setUserId(userId);
            fileEntity.setRubricId(request.getRubricId());
            fileEntity.setFileName(fileName);
            fileEntity.setMinioKey(objectKey);
            fileEntity.setIsPublic(request.getIsPublic() != null ? request.getIsPublic() : false);
            fileMapper.insert(fileEntity);

            // 6. 返回结果
            RubricGenerateResponse response = new RubricGenerateResponse();
            response.setFileId(fileEntity.getId());
            response.setFileName(fileName);
            response.setDownloadUrl("/api/files/presigned/" + fileEntity.getId());
            response.setCreateTime(fileEntity.getCreateTime());

            return Result.success("HTML生成成功", response);
        } catch (Exception e) {
            log.error("[生成HTML] 生成失败", e);
            return Result.error("生成HTML失败");
        }
    }

    /**
     * 生成HTML内容 - 从模板文件读取
     */
    private String generateHtmlContent(RubricEntity rubric, List<QuestionEntity> questions) {
        try {
            // 1. 读取模板文件
            Resource resource = new ClassPathResource("templates/rubric-template.html");
            InputStream inputStream = resource.getInputStream();
            String template = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            
            // 2. 准备数据
            String title = escapeHtml(rubric.getTitle());
            String questionCount = String.valueOf(questions.size());
            
            // 3. 替换标题和题目数量
            template = template.replace("{{title}}", title);
            template = template.replace("{{questionCount}}", questionCount);
            
            // 4. 生成题目列表
            StringBuilder questionsHtml = new StringBuilder();
            for (int i = 0; i < questions.size(); i++) {
                QuestionEntity q = questions.get(i);
                String questionText = escapeHtml(q.getQuestionText());
                String questionType = getTypeLabel(q.getQuestionType());
                String answer = escapeHtml(q.getAnswer());
                String explanation = escapeHtml(q.getExplanation());
                
                // 解析选项
                List<String> options = parseOptions(q.getOptionsJson());
                
                questionsHtml.append("            <div class=\"question-item\" data-question-index=\"").append(i).append("\" data-answer=\"").append(answer).append("\">\n");
                questionsHtml.append("                <div class=\"question-header\">\n");
                questionsHtml.append("                    <h3>").append(i + 1).append(". ").append(questionText).append("</h3>\n");
                questionsHtml.append("                    <span class=\"question-type\">").append(questionType).append("</span>\n");
                questionsHtml.append("                </div>\n");
                
                // 选项
                if (options != null && !options.isEmpty()) {
                    questionsHtml.append("                <div class=\"options\">\n");
                    for (String option : options) {
                        questionsHtml.append("                    <div class=\"option\" onclick=\"selectOption(this, '").append(escapeHtml(option)).append("')\">").append(escapeHtml(option)).append("</div>\n");
                    }
                    questionsHtml.append("                </div>\n");
                }
                
                // 答案
                if (answer != null && !answer.isEmpty()) {
                    questionsHtml.append("                <div class=\"answer\">\n");
                    questionsHtml.append("                    ✓ 答案：").append(answer).append("\n");
                    questionsHtml.append("                </div>\n");
                }
                
                // 解析
                if (explanation != null && !explanation.isEmpty()) {
                    questionsHtml.append("                <div class=\"explanation\">\n");
                    questionsHtml.append("                    💡 解析：").append(explanation).append("\n");
                    questionsHtml.append("                </div>\n");
                }
                
                questionsHtml.append("            </div>\n");
            }
            
            // 5. 替换题目列表
            template = template.replace("{{questions}}", questionsHtml.toString());
            
            return template;
        } catch (Exception e) {
            log.error("读取模板文件失败: {}", e.getMessage(), e);
            // 降级：返回简单的HTML
            return generateSimpleHtml(rubric, questions);
        }
    }
    
    /**
     * 降级方案：生成简单HTML
     */
    private String generateSimpleHtml(RubricEntity rubric, List<QuestionEntity> questions) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\"><title>").append(escapeHtml(rubric.getTitle())).append("</title></head><body>");
        sb.append("<h1>").append(escapeHtml(rubric.getTitle())).append("</h1>");
        sb.append("<p>共 ").append(questions.size()).append(" 道题目</p>");
        
        for (int i = 0; i < questions.size(); i++) {
            QuestionEntity q = questions.get(i);
            sb.append("<div class=\"question\"><h3>").append(i + 1).append(". ").append(escapeHtml(q.getQuestionText())).append("</h3>");
            List<String> options = parseOptions(q.getOptionsJson());
            if (options != null && !options.isEmpty()) {
                sb.append("<ul>");
                for (String opt : options) {
                    sb.append("<li>").append(escapeHtml(opt)).append("</li>");
                }
                sb.append("</ul>");
            }
            if (q.getAnswer() != null && !q.getAnswer().isEmpty()) {
                sb.append("<p><strong>答案：</strong>").append(escapeHtml(q.getAnswer())).append("</p>");
            }
            if (q.getExplanation() != null && !q.getExplanation().isEmpty()) {
                sb.append("<p><strong>解析：</strong>").append(escapeHtml(q.getExplanation())).append("</p>");
            }
            sb.append("</div>");
        }
        sb.append("</body></html>");
        return sb.toString();
    }
    
    /**
     * 解析选项JSON
     */
    private List<String> parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isEmpty()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(optionsJson, List.class);
        } catch (Exception e) {
            log.warn("解析选项失败: {}", e.getMessage());
            return List.of();
        }
    }
    
    /**
     * 获取题型标签
     */
    private String getTypeLabel(String questionType) {
        if (questionType == null) return "未知";
        switch (questionType) {
            case "single_choice": return "单选题";
            case "multiple_choice": return "多选题";
            case "true_false": return "判断题";
            case "short_answer": return "简答题";
            case "calculation": return "计算题";
            default: return questionType;
        }
    }
    
    /**
     * 转换为JSON字符串
     */
    private String toJsonString(String str) {
        if (str == null) return "\"\"";
        return "\"" + escapeHtml(str).replace("\"", "\\\"") + "\"";
    }
    
    /**
     * 转换为JSON字符串数组
     */
    private String toJsonStringArray(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(toJsonString(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * HTML转义
     */
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}
