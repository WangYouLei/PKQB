package com.example.pkqb.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 应用配置
 */
@Configuration
public class AppConfig {

    @Value("${app.upload.path:./uploads}")
    private String uploadPath;

    @Value("${app.generated.path:./generated}")
    private String generatedPath;

    public String getUploadPath() {
        return uploadPath;
    }

    public String getGeneratedPath() {
        return generatedPath;
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
