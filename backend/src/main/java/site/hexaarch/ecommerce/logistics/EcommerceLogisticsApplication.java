package site.hexaarch.ecommerce.logistics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot应用程序主类，作为跨境大卖ERP系统的入口点。
 *
 * @author kenyon
 */
@SpringBootApplication
@ComponentScan(basePackages = {
        "site.hexaarch.ecommerce.logistics.application",
        "site.hexaarch.ecommerce.logistics.domain",
        "site.hexaarch.ecommerce.logistics.infrastructure",
        "site.hexaarch.ecommerce.logistics.interfaces"
})
public class EcommerceLogisticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcommerceLogisticsApplication.class, args);
    }

}