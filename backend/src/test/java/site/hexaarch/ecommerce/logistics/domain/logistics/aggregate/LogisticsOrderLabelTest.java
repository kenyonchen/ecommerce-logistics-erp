package site.hexaarch.ecommerce.logistics.domain.logistics.aggregate;

import org.junit.jupiter.api.Test;
import site.hexaarch.ecommerce.logistics.domain.logistics.event.LogisticsLabelGeneratedEvent;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsLabel;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 面单值对象与 {@link LogisticsOrder#generateLabel(LogisticsLabel)} 的 ID 不变式测试（ISS-115）。
 * <p>
 * 回归点：服务层构建面单时不指定 labelId，而 {@code LogisticsLabelGeneratedEvent} 对 labelId 做
 * {@code requireNonNull}。修复前该组合让 {@code POST /api/logistics/{id}/label} 必然抛
 * {@code Label ID cannot be null} 并返回 500，面单能力在真实环境完全不可用。
 * </p>
 *
 * @author kenyon
 */
class LogisticsOrderLabelTest {

    private LogisticsOrder pendingOrder() {
        LogisticsOrder order = LogisticsOrder.create("tenant-1", UUID.randomUUID().toString(), "channel-1");
        order.clearDomainEvents();
        return order;
    }

    private LogisticsLabel labelWithoutId(String trackingNumber) {
        return LogisticsLabel.builder()
                .trackingNumber(trackingNumber)
                .packageInfo(LogisticsLabel.PackageInfo.builder().build())
                .labelFormat("PDF")
                .build();
    }

    @Test
    void builderMintsLabelIdWhenCallerOmitsIt() {
        LogisticsLabel label = labelWithoutId("TN-" + UUID.randomUUID());

        assertNotNull(label.getLabelId());
        assertFalse(label.getLabelId().isBlank());
    }

    @Test
    void builderKeepsExplicitLabelId() {
        String explicit = "label-" + UUID.randomUUID();

        LogisticsLabel label = LogisticsLabel.builder()
                .labelId(explicit)
                .trackingNumber("TN-1")
                .build();

        assertEquals(explicit, label.getLabelId());
    }

    @Test
    void separatelyBuiltLabelsGetDistinctIds() {
        String first = labelWithoutId("TN-1").getLabelId();
        String second = labelWithoutId("TN-2").getLabelId();

        assertNotNull(first);
        assertNotNull(second);
        assertFalse(first.equals(second));
    }

    @Test
    void generateLabelPublishesEventCarryingTheSameIdThatIsAttachedToTheOrder() {
        LogisticsOrder order = pendingOrder();
        LogisticsLabel label = labelWithoutId(order.getTrackingNumber());

        order.generateLabel(label);

        assertSame(label, order.getLogisticsLabel());
        assertEquals(LogisticsStatus.LABEL_GENERATED, order.getLogisticsStatus());
        LogisticsLabelGeneratedEvent event = order.getDomainEvents().stream()
                .filter(LogisticsLabelGeneratedEvent.class::isInstance)
                .map(LogisticsLabelGeneratedEvent.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("generateLabel 未登记 LogisticsLabelGeneratedEvent"));
        // 事件里的 ID 必须与聚合上落库的面单 ID 是同一个，否则订阅方拿到的是查无此行的 ID
        assertEquals(order.getLogisticsLabel().getLabelId(), event.getLabelId());
        assertEquals(order.getLogisticsOrderId(), event.getLogisticsOrderId());
        assertEquals(order.getOrderId(), event.getOrderId());
        assertEquals(order.getTrackingNumber(), event.getTrackingNumber());
        assertNotNull(event.getGeneratedAt());
    }

    @Test
    void labelRequiresTrackingNumberAndOrderStaysUntouchedWithoutGenerating() {
        LogisticsOrder order = pendingOrder();

        assertThrows(NullPointerException.class, () -> LogisticsLabel.builder()
                .packageInfo(LogisticsLabel.PackageInfo.builder().build())
                .build());
        assertTrue(order.getDomainEvents().isEmpty());
        assertEquals(LogisticsStatus.PENDING, order.getLogisticsStatus());
    }
}
