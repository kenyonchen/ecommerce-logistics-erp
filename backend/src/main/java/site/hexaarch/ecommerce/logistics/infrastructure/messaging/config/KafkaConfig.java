package site.hexaarch.ecommerce.logistics.infrastructure.messaging.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka配置类，创建默认主题。
 *
 * @author kenyon
 */
@Configuration
public class KafkaConfig {
    /**
     * 创建订单事件主题
     */
    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name("order-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * 创建物流事件主题
     */
    @Bean
    public NewTopic logisticsEventsTopic() {
        return TopicBuilder.name("logistics-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * 创建库存事件主题
     */
    @Bean
    public NewTopic inventoryEventsTopic() {
        return TopicBuilder.name("inventory-events")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
