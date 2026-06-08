package pkqb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.util.HtmlUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pkqb.common.Result;
import pkqb.mapper.FileMapper;
import pkqb.mapper.QuestionMapper;
import pkqb.mapper.RubricMapper;
import pkqb.mapper.UserMapper;
import pkqb.mapper.ClassMapper;
import pkqb.pojo.dto.AiRubric;
import pkqb.pojo.dto.RubricGenerateRequest;
import pkqb.pojo.dto.RubricGenerateResponse;
import pkqb.pojo.dto.RubricRequest;
import pkqb.pojo.entity.FileEntity;
import pkqb.pojo.entity.QuestionEntity;
import pkqb.pojo.entity.QuestionResourceEntity;
import pkqb.pojo.entity.RubricEntity;
import pkqb.pojo.entity.UserEntity;
import pkqb.pojo.entity.ClassEntity;
import pkqb.service.MinioService;
import pkqb.service.NotificationService;
import pkqb.service.OssService;
import pkqb.service.QuestionResourceService;
import pkqb.service.RubricService;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RubricServiceImpl implements RubricService {

    private final RubricMapper rubricMapper;
    private final QuestionMapper questionMapper;
    private final FileMapper fileMapper;
    private final UserMapper userMapper;
    private final ClassMapper classMapper;
    private final MinioService minioService;
    private final OssService ossService;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final QuestionResourceService questionResourceService;

    @Value("${minio.endpoint}")
    private String minioEndpoint;

    @Value("${minio.bucket-name}")
    private String bucketName;

    /**
     * 规范化资源类型：将旧的非标准 type（如 "image"/"img"）统一为前端期望的类型
     */

    /**
     * 将OSS图片迁移到MinIO：下载OSS图片→上传到MinIO→替换URL→删除OSS原文件
     * @param originalUrl 原始URL（可能是OSS URL）
     * @return 迁移后的MinIO URL；如果不需要迁移则返回原始URL
     */
    private String migrateOssImageToMinio(String originalUrl) {
        if (originalUrl == null || originalUrl.isEmpty() || !ossService.isOssUrl(originalUrl)) {
            return originalUrl;
        }
        try {
            String objectKey = ossService.extractObjectKeyFromUrl(originalUrl);
            if (objectKey == null || objectKey.isEmpty()) {
                log.warn("[OSS迁移] 无法从URL提取objectKey: {}", originalUrl);
                return originalUrl;
            }

            // 从OSS下载图片
            OssService.OssFileData fileData = ossService.download(objectKey);

            // 生成MinIO存储路径：保留原始路径结构中的文件名部分
            String minioObjectKey = "question-image/" + UUID.randomUUID() + "-" +
                    objectKey.substring(objectKey.lastIndexOf('/') + 1);

            // 上传到MinIO
            ByteArrayInputStream inputStream = new ByteArrayInputStream(fileData.getData());
            minioService.upload(minioObjectKey, inputStream, fileData.getContentType(), fileData.getData().length);

            // 生成MinIO访问URL
            String minioUrl = minioService.getFileUrl(minioObjectKey);

            // 删除OSS原文件
            ossService.delete(objectKey);

            log.info("[OSS迁移] 图片迁移成功: {} → {}", originalUrl, minioUrl);
            return minioUrl;
        } catch (Exception e) {
            log.error("[OSS迁移] 图片迁移失败，保留原OSS URL: {}, error: {}", originalUrl, e.getMessage());
            return originalUrl;
        }
    }

    /**
     * 迁移题目资源列表中的所有OSS图片到MinIO
     * @param resources 题目资源列表
     */

    /**
     * 规范化资源类型：将旧的非标准 type（如 "image"/"img"）统一为前端期望的类型
     */
    private void normalizeResourceType(QuestionResourceEntity res) {
        if (res.getType() == null) return;
        String type = res.getType().toLowerCase();
        switch (type) {
            case "question_image": res.setType("question_image"); break;
            case "option_image": res.setType("option_image"); break;
            case "answer_image": res.setType("answer_image"); break;
            case "explanation_image": res.setType("explanation_image"); break;
            case "image":
            case "img":
                // 未分类图片，根据 label 推断类型
                if (res.getLabel() != null && res.getLabel().matches("^[A-Da-d]$")) {
                    res.setType("option_image");
                    res.setLabel(res.getLabel().toUpperCase());
                } else {
                    res.setType("question_image");
                }
                break;
            default: res.setType("question_image"); break;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> addRubric(RubricRequest rubricRequest) {
        // 1. 保存试卷
        RubricEntity rubricEntity = new RubricEntity();
        rubricEntity.setTitle(HtmlUtils.htmlEscape(rubricRequest.getTitle()));
        rubricEntity.setClassName(HtmlUtils.htmlEscape(rubricRequest.getClassName()));
        rubricEntity.setCreateId(rubricRequest.getCreateId());
        rubricEntity.setCreateStudentNo(rubricRequest.getCreateStudentNo());
        rubricEntity.setIsPrivate(rubricRequest.getIsPrivate());
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
                questionEntity.setQuestionText(HtmlUtils.htmlEscape(aiRubric.getQuestion()));
                questionEntity.setQuestionType(aiRubric.getQuestionType());
                questionEntity.setAnswer(HtmlUtils.htmlEscape(aiRubric.getAnswer()));
                questionEntity.setExplanation(HtmlUtils.htmlEscape(aiRubric.getExplanation()));
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
                    throw new RuntimeException("题目数据序列化失败", e);
                }

                questionMapper.insert(questionEntity);

                if (aiRubric.getResources() != null && !aiRubric.getResources().isEmpty()) {
                    int sortOrder = 0;
                    for (AiRubric.AiResource resource : aiRubric.getResources()) {
                        // 将OSS图片迁移到MinIO，获取新的MinIO URL
                        String imageUrl = resource.getUrl();
                        imageUrl = migrateOssImageToMinio(imageUrl);

                        questionResourceService.saveResource(
                                questionEntity.getId(),
                                resource.getType(),
                                resource.getLabel(),
                                imageUrl,
                                null,
                                sortOrder++
                        );
                    }
                }
            }
        }

        return Result.success("试卷添加成功");
    }

    @Override
    public Result<List<RubricEntity>> getRubricsByUserId(Long userId) {
        try {
            LambdaQueryWrapper<RubricEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(RubricEntity::getCreateId, userId)
                   .eq(RubricEntity::getDeleted, 0)
                   .orderByDesc(RubricEntity::getCreateTime);
            List<RubricEntity> rubrics = rubricMapper.selectList(wrapper);
            
            // 填充创建者昵称
            UserEntity creator = userMapper.selectById(userId);
            if (creator != null) {
                for (RubricEntity rubric : rubrics) {
                    rubric.setCreatorName(creator.getUsername());
                }
            }
            
            return Result.success(rubrics);
        } catch (Exception e) {
            log.error("[获取用户试卷] 获取失败", e);
            return Result.error("获取试卷失败");
        }
    }

    @Override
    public Result<List<RubricEntity>> getPublicRubrics(Long userId) {
        try {
            // 先获取用户的班级信息
            UserEntity user = userMapper.selectById(userId);
            if (user == null || user.getClassId() == null) {
                return Result.success(List.of());
            }
            
            // 通过classId获取班级名称
            ClassEntity classEntity = classMapper.selectById(user.getClassId());
            if (classEntity == null) {
                return Result.success(List.of());
            }
            
            LambdaQueryWrapper<RubricEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(RubricEntity::getIsPrivate, false)  // 公开试卷 isPrivate=false
                   .eq(RubricEntity::getClassName, classEntity.getClassName())  // 同班级（使用班级名称）
                   .ne(RubricEntity::getCreateId, userId)  // 排除当前用户
                   .eq(RubricEntity::getDeleted, 0)
                   .orderByDesc(RubricEntity::getCreateTime);
            List<RubricEntity> rubrics = rubricMapper.selectList(wrapper);
            
            // 填充创建者昵称 - 批量查询
            List<Long> creatorIds = rubrics.stream()
                    .map(RubricEntity::getCreateId)
                    .distinct()
                    .collect(Collectors.toList());
            if (!creatorIds.isEmpty()) {
                List<UserEntity> creators = userMapper.selectBatchIds(creatorIds);
                Map<Long, String> creatorNameMap = creators.stream()
                        .collect(Collectors.toMap(UserEntity::getId, UserEntity::getUsername, (a, b) -> a));
                for (RubricEntity rubric : rubrics) {
                    rubric.setCreatorName(creatorNameMap.get(rubric.getCreateId()));
                }
            }
            
            return Result.success(rubrics);
        } catch (Exception e) {
            log.error("[获取公开试卷] 获取失败", e);
            return Result.error("获取公开试卷失败");
        }
    }

    @Override
    public Result<List<QuestionEntity>> getQuestionsByRubricId(Long rubricId, Long userId) {
        try {
            // 权限校验：私有试卷只有创建者可查看题目
            RubricEntity rubric = rubricMapper.selectById(rubricId);
            if (rubric == null) {
                throw new IllegalArgumentException("试卷不存在");
            }
            if (rubric.getIsPrivate() != null && rubric.getIsPrivate() && !rubric.getCreateId().equals(userId)) {
                throw new IllegalArgumentException("无权查看该试卷的题目");
            }

            LambdaQueryWrapper<QuestionEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(QuestionEntity::getRubricId, rubricId)
                   .eq(QuestionEntity::getDeleted, 0)
                   .orderByAsc(QuestionEntity::getOrderIndex);
            List<QuestionEntity> questions = questionMapper.selectList(wrapper);

            if (!questions.isEmpty()) {
                List<Long> questionIds = questions.stream().map(QuestionEntity::getId).collect(Collectors.toList());
                Map<Long, List<QuestionResourceEntity>> resourceMap = questionResourceService.getByQuestionIds(questionIds);
                for (QuestionEntity question : questions) {
                    List<QuestionResourceEntity> resources = resourceMap.getOrDefault(question.getId(), List.of());
                    // 规范化 type 字段：旧数据可能存的是 "image"，统一为前端期望的类型
                    for (QuestionResourceEntity res : resources) {
                        normalizeResourceType(res);
                    }
                    question.setResources(resources);
                }
            }
            return Result.success(questions);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("[获取题目] 获取失败", e);
            return Result.error("获取题目失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> updateRubric(RubricRequest rubricRequest, Long userId) {
        RubricEntity rubric = rubricMapper.selectById(rubricRequest.getId());
        if (rubric == null) {
            throw new IllegalArgumentException("试卷不存在");
        }
        if (!rubric.getCreateId().equals(userId)) {
            throw new IllegalArgumentException("只有创建者可以修改");
        }

        rubric.setTitle(HtmlUtils.htmlEscape(rubricRequest.getTitle()));
        rubric.setClassName(HtmlUtils.htmlEscape(rubricRequest.getClassName()));
        rubric.setIsPrivate(rubricRequest.getIsPrivate());
        rubric.setUpdateTime(LocalDateTime.now());
        rubricMapper.updateById(rubric);

        return Result.success("修改成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> deleteRubric(Long rubricId, Long userId) {
        RubricEntity rubric = rubricMapper.selectById(rubricId);
        if (rubric == null) {
            throw new IllegalArgumentException("试卷不存在");
        }
        if (!rubric.getCreateId().equals(userId)) {
            throw new IllegalArgumentException("只有创建者可以删除");
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

        if (!questions.isEmpty()) {
            List<Long> questionIds = questions.stream().map(QuestionEntity::getId).collect(Collectors.toList());
            // 批量软删资源
            questionResourceService.softDeleteByQuestionIds(questionIds);
            // 批量软删题目
            QuestionEntity updateEntity = new QuestionEntity();
            updateEntity.setDeleted(1);
            updateEntity.setUpdateTime(LocalDateTime.now());
            LambdaQueryWrapper<QuestionEntity> updateWrapper = new LambdaQueryWrapper<>();
            updateWrapper.in(QuestionEntity::getId, questionIds);
            questionMapper.update(updateEntity, updateWrapper);
        }

        return Result.success("删除成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> updateQuestion(QuestionEntity questionEntity, Long userId) {
        QuestionEntity question = questionMapper.selectById(questionEntity.getId());
        if (question == null) {
            throw new IllegalArgumentException("题目不存在");
        }

        RubricEntity rubric = rubricMapper.selectById(question.getRubricId());
        if (rubric == null) {
            throw new IllegalArgumentException("试卷不存在");
        }
        if (!rubric.getCreateId().equals(userId)) {
            throw new IllegalArgumentException("只有创建者可以修改题目");
        }

        question.setQuestionText(HtmlUtils.htmlEscape(questionEntity.getQuestionText()));
        question.setQuestionType(questionEntity.getQuestionType());
        question.setOptionsJson(questionEntity.getOptionsJson());
        question.setAnswer(HtmlUtils.htmlEscape(questionEntity.getAnswer()));
        question.setExplanation(HtmlUtils.htmlEscape(questionEntity.getExplanation()));
        question.setCalculationStepsJson(questionEntity.getCalculationStepsJson());
        question.setUpdateTime(LocalDateTime.now());
        questionMapper.updateById(question);

        return Result.success("修改题目成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> batchSaveQuestions(Long rubricId, List<QuestionEntity> questions, Long userId) {
        RubricEntity rubric = rubricMapper.selectById(rubricId);
        if (rubric == null) {
            throw new IllegalArgumentException("试卷不存在");
        }
        if (!rubric.getCreateId().equals(userId)) {
            throw new IllegalArgumentException("只有创建者可以修改题目");
        }
        if (questions == null || questions.isEmpty()) {
            throw new IllegalArgumentException("题目列表不能为空");
        }

        LambdaQueryWrapper<QuestionEntity> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(QuestionEntity::getRubricId, rubricId);
        List<QuestionEntity> existingQuestions = questionMapper.selectList(deleteWrapper);
        for (QuestionEntity eq : existingQuestions) {
            questionResourceService.deleteByQuestionId(eq.getId());
        }
        questionMapper.delete(deleteWrapper);

        int orderIndex = 1;
        for (QuestionEntity q : questions) {
            q.setId(null);
            q.setRubricId(rubricId);
            q.setDeleted(0);
            q.setOrderIndex(orderIndex++);
            q.setQuestionText(HtmlUtils.htmlEscape(q.getQuestionText()));
            q.setAnswer(HtmlUtils.htmlEscape(q.getAnswer()));
            q.setExplanation(HtmlUtils.htmlEscape(q.getExplanation()));
            questionMapper.insert(q);

            // 保存资源（如果有），同时将OSS图片迁移到MinIO
            if (q.getResources() != null && !q.getResources().isEmpty()) {
                int sortOrder = 0;
                for (QuestionResourceEntity res : q.getResources()) {
                    String imageUrl = res.getUrl();
                    imageUrl = migrateOssImageToMinio(imageUrl);
                    questionResourceService.saveResource(
                            q.getId(),
                            res.getType(),
                            res.getLabel(),
                            imageUrl,
                            res.getMimeType(),
                            sortOrder++
                    );
                }
            }
        }

        rubric.setQuestionCount(questions.size());
        rubric.setUpdateTime(LocalDateTime.now());
        rubricMapper.updateById(rubric);

        log.info("[批量保存题目] 保存成功，rubricId={}, count={}", rubricId, questions.size());
        return Result.success("保存成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<RubricGenerateResponse> generateHtml(RubricGenerateRequest request, Long userId) {
        // 1. 获取Rubric
        RubricEntity rubric = rubricMapper.selectById(request.getRubricId());
        if (rubric == null) {
            throw new IllegalArgumentException("试卷不存在");
        }

        // 2. 检查是否已经存在HTML文件
        LambdaQueryWrapper<FileEntity> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(FileEntity::getRubricId, request.getRubricId())
                   .eq(FileEntity::getUserId, userId);
        FileEntity existFile = fileMapper.selectOne(existWrapper);
        if (existFile != null) {
            throw new IllegalArgumentException("HTML文件已经存在，请在MinIO文件中查看");
        }

        // 3. 获取所有题目
        LambdaQueryWrapper<QuestionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionEntity::getRubricId, request.getRubricId())
               .eq(QuestionEntity::getDeleted, 0)
               .orderByAsc(QuestionEntity::getOrderIndex);
        List<QuestionEntity> questions = questionMapper.selectList(wrapper);

        // 4. 生成HTML内容
        String htmlContent = generateHtmlContent(rubric, questions);

        // 5. 上传到MinIO
        String fileName = request.getFileName() != null && !request.getFileName().isEmpty()
                ? request.getFileName()
                : rubric.getTitle() + ".html";
        String objectKey = "rubric/" + userId + "/" + UUID.randomUUID() + ".html";

        // 先插入 DB
        FileEntity fileEntity = new FileEntity();
        fileEntity.setUserId(userId);
        fileEntity.setRubricId(request.getRubricId());
        fileEntity.setFileName(fileName);
        fileEntity.setMinioKey(objectKey);
        fileEntity.setIsPrivate(request.getIsPrivate() != null ? request.getIsPrivate() : false);
        fileMapper.insert(fileEntity);

        // 再上传 Minio（DB 已成功，Minio 失败则 DB 回滚但 Minio 文件孤立，可接受）
        ByteArrayInputStream inputStream = new ByteArrayInputStream(htmlContent.getBytes(StandardCharsets.UTF_8));
        minioService.upload(objectKey, inputStream, "text/html", htmlContent.getBytes(StandardCharsets.UTF_8).length);

        // 7. 返回结果
        RubricGenerateResponse response = new RubricGenerateResponse();
        response.setFileId(fileEntity.getId());
        response.setFileName(fileName);
        response.setDownloadUrl(minioEndpoint + "/" + bucketName + "/" + objectKey);
        response.setCreateTime(fileEntity.getCreateTime());

        notificationService.notifyHtmlGenerateComplete(userId, rubric.getTitle());
        return Result.success("HTML生成成功", response);
    }

    /**
     * 生成HTML内容 - 从模板文件读取
     */
    private String generateHtmlContent(RubricEntity rubric, List<QuestionEntity> questions) {
        try {
            // 1. 读取模板文件
            Resource resource = new ClassPathResource("templates/rubric-template.html");
            String template;
            try (InputStream inputStream = resource.getInputStream()) {
                template = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
            
            // 2. 准备数据（数据库中已转义，无需再次转义）
            String title = rubric.getTitle();
            String questionCount = String.valueOf(questions.size());

            // 3. 替换标题和题目数量
            template = template.replace("{{title}}", title);
            template = template.replace("{{questionCount}}", questionCount);

            // 4. 生成题目列表
            StringBuilder questionsHtml = new StringBuilder();
            for (int i = 0; i < questions.size(); i++) {
                QuestionEntity q = questions.get(i);
                String questionText = q.getQuestionText();
                String questionType = getTypeLabel(q.getQuestionType());
                String answer = q.getAnswer();
                String explanation = q.getExplanation();
                
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
                        questionsHtml.append("                    <div class=\"option\" onclick=\"selectOption(this, '").append(option).append("')\">").append(option).append("</div>\n");
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
        sb.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\"><title>").append(rubric.getTitle()).append("</title></head><body>");
        sb.append("<h1>").append(rubric.getTitle()).append("</h1>");
        sb.append("<p>共 ").append(questions.size()).append(" 道题目</p>");

        for (int i = 0; i < questions.size(); i++) {
            QuestionEntity q = questions.get(i);
            sb.append("<div class=\"question\"><h3>").append(i + 1).append(". ").append(q.getQuestionText()).append("</h3>");
            List<String> options = parseOptions(q.getOptionsJson());
            if (options != null && !options.isEmpty()) {
                sb.append("<ul>");
                for (String opt : options) {
                    sb.append("<li>").append(opt).append("</li>");
                }
                sb.append("</ul>");
            }
            if (q.getAnswer() != null && !q.getAnswer().isEmpty()) {
                sb.append("<p><strong>答案：</strong>").append(q.getAnswer()).append("</p>");
            }
            if (q.getExplanation() != null && !q.getExplanation().isEmpty()) {
                sb.append("<p><strong>解析：</strong>").append(q.getExplanation()).append("</p>");
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
        return "\"" + str.replace("\"", "\\\"") + "\"";
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> batchDeleteRubrics(List<Long> rubricIds, Long userId) {
        if (rubricIds == null || rubricIds.isEmpty()) {
            throw new IllegalArgumentException("请选择要删除的试卷");
        }

        // 批量查询所有待删除的试卷
        List<RubricEntity> rubrics = rubricMapper.selectBatchIds(rubricIds);
        List<RubricEntity> validRubrics = rubrics.stream()
                .filter(r -> r.getDeleted() == 0 && r.getCreateId().equals(userId))
                .collect(Collectors.toList());

        if (validRubrics.isEmpty()) {
            throw new IllegalArgumentException("没有可删除的试卷（不存在或无权限）");
        }

        List<Long> validRubricIds = validRubrics.stream().map(RubricEntity::getId).collect(Collectors.toList());

        // 批量软删除试卷
        RubricEntity rubricUpdate = new RubricEntity();
        rubricUpdate.setDeleted(1);
        rubricUpdate.setUpdateTime(LocalDateTime.now());
        LambdaQueryWrapper<RubricEntity> rubricWrapper = new LambdaQueryWrapper<>();
        rubricWrapper.in(RubricEntity::getId, validRubricIds);
        rubricMapper.update(rubricUpdate, rubricWrapper);

        // 查询所有待删除的题目
        LambdaQueryWrapper<QuestionEntity> questionQueryWrapper = new LambdaQueryWrapper<>();
        questionQueryWrapper.in(QuestionEntity::getRubricId, validRubricIds)
                            .eq(QuestionEntity::getDeleted, 0);
        List<QuestionEntity> questions = questionMapper.selectList(questionQueryWrapper);

        if (!questions.isEmpty()) {
            List<Long> questionIds = questions.stream().map(QuestionEntity::getId).collect(Collectors.toList());
            // 批量软删资源
            questionResourceService.softDeleteByQuestionIds(questionIds);
            // 批量软删题目
            QuestionEntity questionUpdate = new QuestionEntity();
            questionUpdate.setDeleted(1);
            questionUpdate.setUpdateTime(LocalDateTime.now());
            LambdaQueryWrapper<QuestionEntity> questionUpdateWrapper = new LambdaQueryWrapper<>();
            questionUpdateWrapper.in(QuestionEntity::getId, questionIds);
            questionMapper.update(questionUpdate, questionUpdateWrapper);
        }

        int failCount = rubricIds.size() - validRubrics.size();
        log.info("[批量删除试卷] userId={}, 总数={}, 成功={}, 失败={}", userId, rubricIds.size(), validRubrics.size(), failCount);

        if (failCount > 0) {
            return Result.success(String.format("删除完成，成功 %d 个，失败 %d 个（不存在或无权限）", validRubrics.size(), failCount));
        }
        return Result.success(String.format("成功删除 %d 个试卷", validRubrics.size()));
    }
}
