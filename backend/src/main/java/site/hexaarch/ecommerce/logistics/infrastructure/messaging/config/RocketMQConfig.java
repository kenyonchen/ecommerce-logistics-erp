package site.hexaarch.ecommerce.logistics.infrastructure.messaging.config;

import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * RocketMQ配置类
 *
 * <p>rocketmq-spring-boot-starter 2.2.3 只在 {@code META-INF/spring.factories} 里注册自动配置，
 * Spring Boot 3.x 已不再读取该文件（改为 {@code META-INF/spring/…AutoConfiguration.imports}），
 * 因此不显式 {@code @Import} 时 {@code RocketMQTemplate}、监听器容器等一律不会装配。
 * 这里通过 @Import 把自动配置接回来，模板与生产者改由 starter 依据顶层 {@code rocketmq.*} 配置构建，
 * 不再手工 new 裸模板。</p>
 *
 * <p>整体由 {@code rocketmq.enabled} 开关控制：关闭时既无模板也无消费者容器，
 * 领域事件仅走进程内 {@code EventHandler} 通道，本机不依赖 broker 即可启动。</p>
 *
 * @author kenyon
 */
@Configuration
@ConditionalOnProperty(prefix = "rocketmq", name = "enabled", havingValue = "true")
@Import(RocketMQAutoConfiguration.class)
public class RocketMQConfig {

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
     * 3. 消费者组：
     *    - 每个消费者组对应一个独立的消费进度
     *    - 集群模式下，同一个消费组的多个消费者会负载均衡消费消息
     *
     * 4. 主题（与 DomainEventPublisher.getTopicForEvent 一一对应）：
     *    - 订单事件：order-events；物流事件：logistics-events；库存事件：inventory-events
     *    - 财务事件：finance-events；采购事件：purchase-events
     *    - 客户事件：customer-events；商品事件：product-events；平台事件：platform-events
     *    - 店铺事件：shop-events
     */
}
