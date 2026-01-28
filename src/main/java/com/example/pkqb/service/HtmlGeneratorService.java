package com.example.pkqb.service;

import com.example.pkqb.config.AppConfig;
import com.example.pkqb.exception.BusinessException;
import com.example.pkqb.model.*;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HTML 生成服务
 */
@Slf4j
@Service
public class HtmlGeneratorService {

    private final AppConfig appConfig;
    private final Handlebars handlebars;

    public HtmlGeneratorService(AppConfig appConfig) {
        this.appConfig = appConfig;
        ClassPathTemplateLoader loader = new ClassPathTemplateLoader("/templates", ".html");
        this.handlebars = new Handlebars(loader);
    }

    public AppConfig getAppConfig() {
        return appConfig;
    }

    /**
     * 生成 HTML 文件
     *
     * @param request 生成请求
     * @return 文件信息（文件名和下载URL）
     */
    public Map<String, String> generateHtml(GenerateRequest request) {
        try {
            log.info("开始生成 HTML，类型: {}", request.getType());

            // 确保生成目录存在
            String generatedPath = appConfig.getGeneratedPath();
            Files.createDirectories(Paths.get(generatedPath));

            // 选择模板
            String templateName = selectTemplate(request.getType());
            Template template = handlebars.compile(templateName);

            // 准备模板数据
            Map<String, Object> data = prepareTemplateData(request);

            // 渲染 HTML
            String htmlContent = template.apply(data);

            // 生成文件名
            String fileName = generateFileName(request.getType());
            
            // 保存文件
            String filePath = Paths.get(generatedPath, fileName).toString();
            saveHtmlFile(filePath, htmlContent);
            log.info("HTML 生成成功: {}", filePath);
            
            // 返回文件信息（使用文件名作为fileId，去掉.html扩展名）
            String fileId = fileName.substring(0, fileName.lastIndexOf('.'));
            Map<String, String> result = new HashMap<>();
            result.put("fileId", fileId);
            result.put("fileName", fileName);
            return result;

        } catch (Exception e) {
            log.error("生成 HTML 失败", e);
            throw new BusinessException("生成 HTML 失败: " + e.getMessage());
        }
    }

    /**
     * 选择模板
     */
    private String selectTemplate(String type) {
        if ("question".equalsIgnoreCase(type)) {
            return "question-template";
        } else if ("note".equalsIgnoreCase(type)) {
            return "note-template";
        } else {
            throw new BusinessException("不支持的类型: " + type);
        }
    }

    /**
     * 准备模板数据
     */
    private Map<String, Object> prepareTemplateData(GenerateRequest request) {
        Map<String, Object> data = new HashMap<>();

        data.put("type", request.getType());
        data.put("title", request.getTitle());
        data.put("items", request.getItems());

        // 如果是题目，添加题目数量
        if ("question".equalsIgnoreCase(request.getType())) {
            int totalQuestions = request.getItems() != null ? request.getItems().size() : 0;
            data.put("totalQuestions", totalQuestions);
        }

        // 如果是笔记，添加笔记数量
        if ("note".equalsIgnoreCase(request.getType())) {
            int totalNotes = request.getItems() != null ? request.getItems().size() : 0;
            data.put("totalNotes", totalNotes);
        }

        return data;
    }

    /**
     * 生成文件名
     */
    private String generateFileName(String type) {
        String prefix = "question".equalsIgnoreCase(type) ? "questions" : "notes";
        return String.format("%s-%d.html", prefix, System.currentTimeMillis());
    }

    /**
     * 保存 HTML 文件
     */
    private void saveHtmlFile(String filePath, String content) throws IOException {
        try (java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(
                new java.io.FileOutputStream(filePath), java.nio.charset.StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }

    /**
     * 读取生成的 HTML 文件
     *
     * @param fileName 文件名
     * @return 文件内容
     */
    public String getGeneratedHtml(String fileName) {
        try {
            String filePath = Paths.get(appConfig.getGeneratedPath(), fileName).toString();
            return new String(Files.readAllBytes(Paths.get(filePath)));
        } catch (IOException e) {
            log.error("读取 HTML 文件失败: {}", fileName, e);
            throw new BusinessException("读取 HTML 文件失败: " + e.getMessage());
        }
    }
}
