package site.hexaarch.ecommerce.logistics.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger配置类，用于配置API文档相关设置。
 *
 * @author kenyon
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components())
                .info(new Info()
                        .title("跨境大卖ERP系统API")
                        .description("跨境大卖ERP系统RESTful API文档")
                        .contact(new Contact()
                                .name("开发团队")
                                .email("dev@example.com"))
                        .version("1.0"));
    }
}