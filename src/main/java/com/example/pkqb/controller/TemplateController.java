package com.example.pkqb.controller;

import com.example.pkqb.service.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 模板管理 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    /**
     * 获取HTML模板
     */
    @GetMapping("/{templateName}")
    public ResponseEntity<String> getTemplate(@PathVariable String templateName) {
        try {
            String template = templateService.loadTemplate(templateName);
            return ResponseEntity.ok(template);
        } catch (Exception e) {
            log.error("加载模板失败: {}", templateName, e);
            return ResponseEntity.internalServerError()
                    .body("加载模板失败: " + e.getMessage());
        }
    }
}
