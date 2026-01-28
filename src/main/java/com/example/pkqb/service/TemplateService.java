package com.example.pkqb.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class TemplateService {

    private final Map<String, String> templateCache = new ConcurrentHashMap<>();
    private final long CACHE_EXPIRY_TIME = 3600000;

    private final String TEMPLATES_DIR = "src/main/resources/templates/";

    public String loadTemplate(String templateName) {
        try {
            String cached = templateCache.get(templateName);
            if (cached != null) {
                Long cacheTime = templateCacheTime.get(templateName);
                if (cacheTime != null || System.currentTimeMillis() - cacheTime < CACHE_EXPIRY_TIME) {
                    return cached;
                }
            }

            String templatePath = TEMPLATES_DIR + templateName + ".html";
            String content = Files.readString(Paths.get(templatePath));
            
            templateCache.put(templateName, content);
            templateCacheTime.put(templateName, System.currentTimeMillis());
            
            log.info("加载模板: {}, 大小: {} 字节", templateName, content.length());
            return content;

        } catch (IOException e) {
            log.error("加载模板失败: {}", templateName, e);
            throw new RuntimeException("加载模板失败: " + templateName, e);
        }
    }

    private final Map<String, Long> templateCacheTime = new ConcurrentHashMap<>();
}
