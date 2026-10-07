package site.hexaarch.ecommerce.logistics.domain.purchase.aggregate;

import org.junit.jupiter.api.Test;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.purchase.entity.PurchaseOrderItem;
import site.hexaarch.ecommerce.logistics.domain.purchase.event.PurchaseOrderStatusChangedEvent;
import site.hexaarch.ecommerce.logistics.domain.purchase.valueobject.PurchaseStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 采购单聚合的状态机、明细不变式与事件登记测试。
 *
 * @author kenyon
 */
class PurchaseOrderTest {

    private static final String TENANT = "tenant-1";
    private static final String WAREHOUSE = "wh-1";

    private PurchaseOrder newOrder() {
        return PurchaseOrder.create("PO-" + UUID.randomUUID(), TENANT, "sup-1", "供应商A", WAREHOUSE);
    }

    private PurchaseOrder orderAt(PurchaseStatus status) {
        return PurchaseOrder.builder()
                .purchaseOrderId("PO-" + UUID.randomUUID())
                .tenantId(TENANT)
                .supplierId("sup-1")
                .supplierName("供应商A")
                .warehouseId(WAREHOUSE)
                .status(status)
                .build();
    }

    @Test
    void pendingShouldOnlyBeApprovableRejectionOrCancellable() {
        assertEquals(List.of(PurchaseStatus.APPROVED, PurchaseStatus.REJECTED, PurchaseStatus.CANCELLED),
                PurchaseStatus.PENDING.allowedNextStatuses());
        // 待处理不能直接跳到运输中/已送达，必须先批准
        assertFalseTransition(PurchaseStatus.PENDING, PurchaseStatus.IN_TRANSIT);
        assertFalseTransition(PurchaseStatus.PENDING, PurchaseStatus.DELIVERED);
    }

    @Test
    void approvedShouldOnlyShipOrCancel() {
        assertEquals(List.of(PurchaseStatus.IN_TRANSIT, PurchaseStatus.CANCELLED),
                PurchaseStatus.APPROVED.allowedNextStatuses());
        assertFalseTransition(PurchaseStatus.APPROVED, PurchaseStatus.DELIVERED);
    }

    @Test
    void inTransitShouldOnlyArriveAndCannotBeCancelledAnymore() {
        assertEquals(List.of(PurchaseStatus.DELIVERED), PurchaseStatus.IN_TRANSIT.allowedNextStatuses());
        // ISS-043：此前「已取消」可从任意状态进入，发货后取消会让库存与应付口径矛盾
        assertFalseTransition(PurchaseStatus.IN_TRANSIT, PurchaseStatus.CANCELLED);
    }

    @Test
    void terminalStatusesAllowNoFurtherTransition() {
        for (PurchaseStatus terminal : List.of(PurchaseStatus.DELIVERED, PurchaseStatus.REJECTED, PurchaseStatus.CANCELLED)) {
            PurchaseOrder purchaseOrder = orderAt(terminal);

            assertThrows(BusinessException.class, () -> purchaseOrder.approve("alice", "同意"));
            assertThrows(BusinessException.class, () -> purchaseOrder.reject("alice", "价格过高"));
            assertThrows(BusinessException.class, purchaseOrder::markAsInTransit);
            assertThrows(BusinessException.class, purchaseOrder::markAsDelivered);
            assertThrows(BusinessException.class, purchaseOrder::cancel);
            assertTrue(terminal.allowedNextStatuses().isEmpty(), terminal + " 应为终态");
        }
    }

    @Test
    void illegalTransitionShouldCarveItsOwnErrorCodeSoUiShowsTheReason() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> orderAt(PurchaseStatus.DELIVERED).cancel());
        assertEquals("INVALID_STATUS_TRANSITION", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("已送达"), exception.getMessage());
        assertTrue(exception.getMessage().contains("已取消"), exception.getMessage());
    }

    @Test
    void approveShouldRecordApproverCommentAndStatus() {
        PurchaseOrder purchaseOrder = newOrder();
        purchaseOrder.addItem("SKU-001", "商品A", 2, new BigDecimal("10.00"));

        purchaseOrder.approve("alice", "预算内，同意");

        assertEquals(PurchaseStatus.APPROVED, purchaseOrder.getStatus());
        assertEquals("alice", purchaseOrder.getApprovedBy());
        assertEquals("预算内，同意", purchaseOrder.getApprovalComment());
        assertNotNull(purchaseOrder.getApprovedAt());
    }

    @Test
    void rejectWithoutReasonShouldBeRejected() {
        PurchaseOrder purchaseOrder = newOrder();

        assertThrows(ValidationException.class, () -> purchaseOrder.reject("bob", "  "));
        assertEquals(PurchaseStatus.PENDING, purchaseOrder.getStatus(), "校验失败不得留下半状态");
    }

    @Test
    void happyPathShouldWalkTheWholeStateMachine() {
        PurchaseOrder purchaseOrder = newOrder();
        purchaseOrder.addItem("SKU-001", "商品A", 2, new BigDecimal("10.00"));
        purchaseOrder.approve("alice", "同意");
        purchaseOrder.markAsInTransit();
        purchaseOrder.markAsDelivered();

        assertEquals(PurchaseStatus.DELIVERED, purchaseOrder.getStatus());
    }

    @Test
    void everyTransitionShouldRegisterStatusChangedEvent() {
        PurchaseOrder purchaseOrder = newOrder();
        purchaseOrder.clearDomainEvents();

        purchaseOrder.approve("alice", "同意");
        purchaseOrder.markAsInTransit();
        purchaseOrder.markAsDelivered();

        List<PurchaseOrderStatusChangedEvent> events = purchaseOrder.getDomainEvents().stream()
                .filter(PurchaseOrderStatusChangedEvent.class::isInstance)
                .map(PurchaseOrderStatusChangedEvent.class::cast)
                .toList();
        assertEquals(3, events.size());
        assertEquals(PurchaseStatus.APPROVED, events.get(0).getNewStatus());
        assertEquals(PurchaseStatus.IN_TRANSIT, events.get(1).getNewStatus());
        assertEquals(PurchaseStatus.DELIVERED, events.get(2).getNewStatus());
    }

    @Test
    void duplicateSkuInOneOrderShouldBeRejectedRegardlessOfCase() {
        PurchaseOrder purchaseOrder = newOrder();
        purchaseOrder.addItem("sku-001", "商品A", 1, new BigDecimal("10.00"));

        // 线上 purchase_order_items 没有 SKU 唯一键，重复行只能在聚合侧拦（docs/db/data-dictionary.md R-05）
        BusinessException exception = assertThrows(BusinessException.class,
                () -> purchaseOrder.addItem("SKU-001", "商品A", 2, new BigDecimal("20.00")));
        assertEquals("DUPLICATE_PURCHASE_ITEM", exception.getErrorCode());
    }

    @Test
    void itemWithBlankOrNonPositiveNumbersShouldBeRejected() {
        PurchaseOrder purchaseOrder = newOrder();

        assertThrows(ValidationException.class, () -> purchaseOrder.addItem("  ", "商品A", 1, new BigDecimal("1.00")));
        assertThrows(ValidationException.class, () -> purchaseOrder.addItem("SKU-001", "  ", 1, new BigDecimal("1.00")));
        assertThrows(ValidationException.class, () -> purchaseOrder.addItem("SKU-001", "商品A", 0, new BigDecimal("1.00")));
        assertThrows(ValidationException.class, () -> purchaseOrder.addItem("SKU-001", "商品A", 1, BigDecimal.ZERO));
        assertThrows(ValidationException.class, () -> purchaseOrder.addItem("SKU-001", "商品A", 1, new BigDecimal("-5.00")));
        assertTrue(purchaseOrder.getItems().isEmpty());
    }

    @Test
    void totalAmountShouldFollowAddedItems() {
        PurchaseOrder purchaseOrder = newOrder();
        purchaseOrder.addItem("SKU-001", "商品A", 2, new BigDecimal("10.00"));
        purchaseOrder.addItem("SKU-002", "商品B", 3, new BigDecimal("5.50"));

        assertEquals(0, new BigDecimal("36.50").compareTo(purchaseOrder.getTotalAmount()));
    }

    @Test
    void updateAndRemoveItemShouldRecalculateTotalFromSubtotals() {
        PurchaseOrder purchaseOrder = persistedOrder(
                item("1", "SKU-001", "商品A", 2, "10.00"),
                item("2", "SKU-002", "商品B", 3, "5.50"));

        purchaseOrder.updateItem("1", 4, new BigDecimal("10.00"));
        assertEquals(0, new BigDecimal("56.50").compareTo(purchaseOrder.getTotalAmount()));

        purchaseOrder.removeItem("2");
        assertEquals(0, new BigDecimal("40.00").compareTo(purchaseOrder.getTotalAmount()));
        assertEquals(1, purchaseOrder.getItems().size());
    }

    @Test
    void updateItemShouldKeepItsIdentityAndRecalculateSubtotal() {
        PurchaseOrder purchaseOrder = persistedOrder(item("1", "SKU-001", "商品A", 2, "10.00"));

        purchaseOrder.updateItem("1", 5, new BigDecimal("9.00"));

        PurchaseOrderItem after = purchaseOrder.getItems().get(0);
        assertEquals("1", after.getId(), "改数量不能换掉主键，否则 PUT 明细会错位");
        assertEquals("SKU-001", after.getSkuCode());
        assertEquals(0, new BigDecimal("45.00").compareTo(after.getTotalPrice()));
    }

    @Test
    void unknownItemIdShouldRaiseNotFound() {
        PurchaseOrder purchaseOrder = persistedOrder(item("1", "SKU-001", "商品A", 2, "10.00"));

        EntityNotFoundException update = assertThrows(EntityNotFoundException.class,
                () -> purchaseOrder.updateItem("999999", 1, BigDecimal.ONE));
        assertEquals("ENTITY_NOT_FOUND", update.getErrorCode());
        assertThrows(EntityNotFoundException.class, () -> purchaseOrder.removeItem("999999"));
    }

    @Test
    void itemMaintenanceMustBeLockedOnceTheOrderLeftPending() {
        for (PurchaseStatus status : List.of(PurchaseStatus.APPROVED, PurchaseStatus.IN_TRANSIT,
                PurchaseStatus.DELIVERED, PurchaseStatus.CANCELLED)) {
            PurchaseOrder purchaseOrder = orderAt(status);

            BusinessException add = assertThrows(BusinessException.class,
                    () -> purchaseOrder.addItem("SKU-001", "商品A", 1, BigDecimal.ONE));
            assertEquals("PURCHASE_ITEMS_LOCKED", add.getErrorCode());
            assertThrows(BusinessException.class, () -> purchaseOrder.removeItem("1"));
            assertThrows(BusinessException.class, () -> purchaseOrder.updateItem("1", 1, BigDecimal.ONE));
        }
    }

    @Test
    void toBuilderShouldCarryApprovalFieldsAndItems() {
        PurchaseOrder source = persistedOrder(item("1", "SKU-001", "商品A", 2, "10.00"));
        source.approve("alice", "同意");

        PurchaseOrder copy = source.toBuilder().build();

        assertEquals("alice", copy.getApprovedBy());
        assertEquals("同意", copy.getApprovalComment());
        assertEquals(PurchaseStatus.APPROVED, copy.getStatus());
        assertEquals(1, copy.getItems().size());
        assertEquals("1", copy.getItems().get(0).getId());
    }

    /**
     * 构造一张「已从数据库读回」的采购单：明细行带主键，改删才可能定位到行。
     */
    private PurchaseOrder persistedOrder(PurchaseOrderItem... items) {
        return PurchaseOrder.builder()
                .purchaseOrderId("PO-1")
                .tenantId(TENANT)
                .supplierId("sup-1")
                .supplierName("供应商A")
                .warehouseId(WAREHOUSE)
                .status(PurchaseStatus.PENDING)
                .items(List.of(items))
                .build();
    }

    private PurchaseOrderItem item(String id, String skuCode, String productName, int quantity, String unitPrice) {
        return PurchaseOrderItem.builder()
                .id(id)
                .skuCode(skuCode)
                .productName(productName)
                .quantity(quantity)
                .unitPrice(new BigDecimal(unitPrice))
                .build();
    }

    private void assertFalseTransition(PurchaseStatus from, PurchaseStatus to) {
        PurchaseOrder purchaseOrder = orderAt(from);
        Runnable action = switch (to) {
            case APPROVED -> () -> purchaseOrder.approve("alice", "同意");
            case REJECTED -> () -> purchaseOrder.reject("alice", "价格过高");
            case IN_TRANSIT -> purchaseOrder::markAsInTransit;
            case DELIVERED -> purchaseOrder::markAsDelivered;
            case CANCELLED -> purchaseOrder::cancel;
            default -> throw new IllegalArgumentException("没有流转回 " + to + " 的动作");
        };
        assertThrows(BusinessException.class, action::run, from + " 不应能流转到 " + to);
    }
}
