package com.example.pkqb.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 静态资源服务
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class StaticResourceController {

    /**
     * 获取Vue.js文件
     */
    @GetMapping("/vue/{fileName}")
    public ResponseEntity<String> getVueFile(@PathVariable String fileName) {
        try {
            String vuePath = "classpath:static/js/" + fileName + ".js";
            ClassPathResource resource = new ClassPathResource(vuePath);
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/javascript;charset=UTF-8"))
                    .body(content);
        } catch (IOException e) {
            log.error("加载Vue文件失败: {}", fileName, e);
            return ResponseEntity.internalServerError()
                    .body("加载Vue文件失败: " + e.getMessage());
        }
    }

    /**
     * 获取CSS文件
     */
    @GetMapping("/css/{fileName}")
    public ResponseEntity<String> getCssFile(@PathVariable String fileName) {
        try {
            String cssPath = "classpath:static/css/" + fileName + ".css";
            ClassPathResource resource = new ClassPathResource(cssPath);
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/css;charset=UTF-8"))
                    .body(content);
        } catch (IOException e) {
            log.error("加载CSS文件失败: {}", fileName, e);
            return ResponseEntity.internalServerError()
                    .body("加载CSS文件失败: " + e.getMessage());
        }
    }
}
