package com.example.pkqb.controller;

import com.example.pkqb.model.*;
import com.example.pkqb.service.ContentAnalysisService;
import com.example.pkqb.service.HtmlGeneratorService;
import com.example.pkqb.service.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 内容处理 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ContentController {

    private final ContentAnalysisService contentAnalysisService;
    private final HtmlGeneratorService htmlGeneratorService;
    private final TemplateService templateService;

    /**
     * AI 内容分析接口
     */
    @PostMapping("/analyze")
    public ResponseEntity<ContentResponse<?>> analyzeContent(
            @RequestBody ContentRequest request) {
        try {
            log.info("收到内容分析请求，文本长度: {}",
                    request.getText() != null ? request.getText().length() : 0);

            // 验证请求参数
            if (request.getText() == null || request.getText().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ContentResponse.error("文本内容不能为空"));
            }

            // 调用 AI 分析
            Map<String, Object> result = contentAnalysisService.analyzeContent(request.getText());
            return ResponseEntity.ok(ContentResponse.success("分析成功", result));

        } catch (Exception e) {
            log.error("内容分析失败", e);
            return ResponseEntity.internalServerError()
                    .body(ContentResponse.error("内容分析失败: " + e.getMessage()));
        }
    }

    /**
     * HTML 生成接口
     */
    @PostMapping("/generate")
    public ResponseEntity<ContentResponse<Map<String, String>>> generateHtml(
            @RequestBody GenerateRequest request) {
        try {
            log.info("收到 HTML 生成请求，类型: {}", request.getType());

            // 验证请求参数
            if (request.getType() == null || request.getType().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ContentResponse.error("类型不能为空"));
            }

            if (request.getItems() == null || request.getItems().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ContentResponse.error("内容项不能为空"));
            }

            // 生成 HTML
            Map<String, String> fileInfo = htmlGeneratorService.generateHtml(request);
            return ResponseEntity.ok(ContentResponse.success("生成成功", fileInfo));

        } catch (Exception e) {
            log.error("HTML 生成失败", e);
            return ResponseEntity.internalServerError()
                    .body(ContentResponse.error("HTML 生成失败: " + e.getMessage()));
        }
    }

    /**
     * 查询生成状态接口
     */
    @GetMapping("/generate/status/{fileId}")
    public ResponseEntity<Map<String, String>> getGenerateStatus(@PathVariable String fileId) {
        try {
            log.info("查询生成状态，文件ID: {}", fileId);

            // 检查生成的文件是否存在
            String generatedPath = htmlGeneratorService.getAppConfig().getGeneratedPath();
            java.nio.file.Path filePath = java.nio.file.Paths.get(generatedPath, fileId + ".html");

            if (java.nio.file.Files.exists(filePath)) {
                // 文件已生成
                Map<String, String> result = new HashMap<>();
                result.put("status", "completed");
                result.put("fileName", fileId + ".html");
                return ResponseEntity.ok(result);
            } else {
                // 文件未生成
                Map<String, String> result = new HashMap<>();
                result.put("status", "generating");
                return ResponseEntity.ok(result);
            }

        } catch (Exception e) {
            log.error("查询生成状态失败", e);
            Map<String, String> errorResult = new HashMap<>();
            errorResult.put("error", "查询失败: " + e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(errorResult);
        }
    }
}
