package site.hexaarch.ecommerce.logistics.infrastructure.messaging.config;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.consumer.InventoryShortageEventConsumer;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.consumer.LogisticsCompletedEventConsumer;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.consumer.OrderCreatedEventConsumer;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.consumer.OrderStatusChangedEventConsumer;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RocketMQ 装配结构与开关测试。
 *
 * <p>starter 2.2.3 只在 META-INF/spring.factories 中注册自动配置，Spring Boot 3 不读取该文件，
 * 必须靠 RocketMQConfig 显式 @Import 才能装配；同时守住 ISS-034 的三个回归点：
 * 监听器必须是可独立实例化的顶层类、不得手工 new 裸模板、rocketmq.enabled 缺省时整套 MQ 完全不装配。</p>
 *
 * @author kenyon
 */
class RocketMQConfigTest {

    private static final List<Class<?>> LISTENER_TYPES = List.of(
            OrderCreatedEventConsumer.class,
            OrderStatusChangedEventConsumer.class,
            LogisticsCompletedEventConsumer.class,
            InventoryShortageEventConsumer.class);

    @Test
    void shouldImportStarterAutoConfigurationInsteadOfHandBuiltTemplate() {
        Import imported = RocketMQConfig.class.getAnnotation(Import.class);
        assertNotNull(imported,
                "RocketMQConfig 必须 @Import RocketMQAutoConfiguration，否则 Boot 3 下 MQ 自动配置不会加载");
        assertTrue(List.of(imported.value()).contains(RocketMQAutoConfiguration.class));

        for (Method method : RocketMQConfig.class.getDeclaredMethods()) {
            assertFalse(method.isAnnotationPresent(Bean.class),
                    "不得再手工声明 RocketMQTemplate Bean，模板必须由 starter 按 rocketmq.* 配置构建: " + method.getName());
        }
    }

    @Test
    void shouldKeepEveryListenerAsIndependentTopLevelClass() {
        for (Class<?> listenerType : LISTENER_TYPES) {
            assertFalse(listenerType.isMemberClass(),
                    listenerType.getSimpleName() + " 必须是顶层类：内部类无法被 Spring 独立实例化，消费者容器不会注册");
            assertTrue(java.lang.reflect.Modifier.isPublic(listenerType.getModifiers()),
                    listenerType.getSimpleName() + " 必须是 public 类");
            assertNotNull(listenerType.getAnnotation(RocketMQMessageListener.class),
                    listenerType.getSimpleName() + " 必须标注 @RocketMQMessageListener");
            assertTrue(RocketMQListener.class.isAssignableFrom(listenerType),
                    listenerType.getSimpleName() + " 必须实现 RocketMQListener");
        }
    }

    @Test
    void shouldKeepTopicAndConsumerGroupPerContext() {
        assertBinding(OrderCreatedEventConsumer.class, "order-events", "logistics-group");
        assertBinding(OrderStatusChangedEventConsumer.class, "order-events", "warehouse-group");
        assertBinding(LogisticsCompletedEventConsumer.class, "logistics-events", "finance-group");
        assertBinding(InventoryShortageEventConsumer.class, "inventory-events", "purchase-group");
    }

    @Test
    void shouldNotWireMqWhenEnabledIsMissingOrFalse() {
        new ApplicationContextRunner()
                .withUserConfiguration(RocketMQConfig.class, OrderCreatedEventConsumer.class)
                .run(context -> {
                    assertFalse(context.containsBean("rocketMQTemplate"),
                            "rocketmq.enabled 缺省时不应装配 RocketMQTemplate");
                    assertFalse(context.containsBean("orderCreatedEventConsumer"),
                            "rocketmq.enabled 缺省时不应注册消费者 Bean");
                });

        new ApplicationContextRunner()
                .withUserConfiguration(RocketMQConfig.class, OrderCreatedEventConsumer.class)
                .withPropertyValues("rocketmq.enabled=false")
                .run(context -> assertFalse(context.containsBean("rocketMQTemplate")));
    }

    @Test
    void shouldNotPublishRocketMqTemplateTypeAsBeanWhenDisabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(RocketMQConfig.class)
                .withPropertyValues("rocketmq.enabled=false",
                        "rocketmq.name-server=localhost:9876",
                        "rocketmq.producer.group=ecommerce-logistics-producer-group")
                .run(context -> assertTrue(context.getBeansOfType(RocketMQTemplate.class).isEmpty(),
                        "enabled=false 是总开关，name-server 单独存在也不应让 MQ 生效"));
    }

    private void assertBinding(Class<?> listenerType, String expectedTopic, String expectedConsumerGroup) {
        RocketMQMessageListener annotation = listenerType.getAnnotation(RocketMQMessageListener.class);
        assertNotNull(annotation, listenerType.getSimpleName() + " 缺少 @RocketMQMessageListener");
        assertEqualsAnnotation(listenerType.getSimpleName() + " topic", expectedTopic, annotation.topic());
        assertEqualsAnnotation(listenerType.getSimpleName() + " consumerGroup",
                expectedConsumerGroup, annotation.consumerGroup());
    }

    private void assertEqualsAnnotation(String what, String expected, String actual) {
        assertTrue(expected.equals(actual), what + " 应为 " + expected + "，实际: " + actual);
    }
}
