package pkqb.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger API文档配置类
 * 配置OpenAPI文档信息
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PKQB 智能题库系统 API")
                        .description("智能题库管理与AI辅助学习系统后端API文档")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("PKQB Team")
                                .email("support@pkqb.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:5555").description("开发服务器")
                ));
    }
}
