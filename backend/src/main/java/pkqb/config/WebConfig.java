package pkqb.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import pkqb.interceptor.AdminInterceptor;
import pkqb.interceptor.JwtInterceptor;
import pkqb.interceptor.LogInterceptor;

import java.util.Arrays;

/**
 * Web MVC 配置
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final LogInterceptor logInterceptor;
    private final AdminInterceptor adminInterceptor;

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:3001,http://localhost:5173,http://localhost:5555}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 日志拦截器（最先执行）
        registry.addInterceptor(logInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/swagger-resources/**",
                        "/webjars/**",
                        "/doc.html",
                        "/favicon.ico"
                );

        // JWT拦截器（在日志之后执行）
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/api/auth/**",
                        "/api/admin/auth/**",
                        "/ws/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/swagger-resources/**",
                        "/webjars/**",
                        "/doc.html",
                        "/favicon.ico"
                );

        // 管理端权限拦截器（在JWT之后执行，校验 role=1）
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/auth/**");
    }
}
