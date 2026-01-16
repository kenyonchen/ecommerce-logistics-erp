package site.hexaarch.ecommerce.logistics.infrastructure.messaging.config;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ配置类
 *
 * @author kenyon
 */
@Configuration
public class RocketMQConfig {

    /**
     * 配置RocketMQTemplate
     *
     * @return RocketMQTemplate实例
     */
    @Bean
    public RocketMQTemplate rocketMQTemplate() {
        return new RocketMQTemplate();
    }

    /**
     * RocketMQ配置说明：
     *
     * 1. 消息消费模式：
     *    - ConsumeMode.CONCURRENTLY: 并发消费（默认）
     *    - ConsumeMode.ORDERLY: 顺序消费
     *
     * 2. 消息模型：
     *    - MessageModel.CLUSTERING: 集群模式（默认）
     *    - MessageModel.BROADCASTING: 广播模式
     *
     * 3. 消息选择器类型：
     *    - SelectorType.TAG: Tag选择器（默认）
     *    - SelectorType.SQL92: SQL92选择器
     *
     * 4. 消费者组：
     *    - 每个消费者组对应一个独立的消费进度
     *    - 集群模式下，同一个消费组的多个消费者会负载均衡消费消息
     *    - 广播模式下，同一个消费组的多个消费者都会收到相同的消息
     *
     * 5. 主题：
     *    - 订单事件主题：order-events
     *    - 物流事件主题：logistics-events
     *    - 库存事件主题：inventory-events
     *    - 财务事件主题：finance-events
     *    - 采购事件主题：purchase-events
     */
}