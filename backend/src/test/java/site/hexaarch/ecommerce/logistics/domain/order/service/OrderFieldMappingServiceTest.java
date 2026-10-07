package site.hexaarch.ecommerce.logistics.domain.order.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import site.hexaarch.ecommerce.logistics.domain.order.entity.OrderFieldMapping;
import site.hexaarch.ecommerce.logistics.domain.order.entity.OrderImportError;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderFieldMappingRepository;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.ImportedOrderItem;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.ImportedOrderRow;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderImportTargetField;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 订单字段映射服务单元测试：覆盖自动识别、平台行转换、行级异常与数值清洗。
 */
class OrderFieldMappingServiceTest {

    private OrderFieldMappingService service;

    @BeforeEach
    void setUp() {
        OrderFieldMappingRepository repository = Mockito.mock(OrderFieldMappingRepository.class);
        when(repository.findByTenantIdAndPlatform(Mockito.any(), Mockito.any())).thenReturn(List.of());
        service = new OrderFieldMappingService(repository);
    }

    private Map<String, Object> amazonRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("amazon-order-id", "AMZ-001");
        row.put("buyer-name", "张三");
        row.put("buyer-email", "zhangsan@example.com");
        row.put("order-status", "Pending");
        row.put("order-total", "¥1,299.00");
        row.put("currency", "CNY");
        row.put("purchase-date", "2026-09-01 10:00:00");
        row.put("payment-status", "PAID");
        row.put("payment-method", "Standard");
        row.put("recipient-name", "李四");
        row.put("phone", "13800138000");
        row.put("country", "中国");
        row.put("state", "广东省");
        row.put("city", "深圳市");
        row.put("address-line1", "南山区科技园路1号");
        row.put("postal-code", "518000");
        row.put("items", List.of(
                item("SKU-001", "商品A", 2, 99.0),
                item("SKU-002", "赠品", 1, 0.0)));
        return row;
    }

    private Map<String, Object> item(String sku, String name, int quantity, double price) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("sku", sku);
        item.put("productName", name);
        item.put("quantity", quantity);
        item.put("unitPrice", price);
        return item;
    }

    @Test
    void shouldAutoDetectMappingsFromPlatformHeaders() {
        List<String> headers = List.of("amazon-order-id", "buyer-name", "order-total", "items", "unknown-column");
        List<OrderFieldMapping> mappings = service.autoDetect(headers);

        List<OrderImportTargetField> targets = mappings.stream()
                .map(OrderFieldMapping::getTargetField)
                .toList();

        assertTrue(targets.contains(OrderImportTargetField.EXTERNAL_ORDER_ID));
        assertTrue(targets.contains(OrderImportTargetField.CUSTOMER_NAME));
        assertTrue(targets.contains(OrderImportTargetField.TOTAL_AMOUNT));
        assertTrue(targets.contains(OrderImportTargetField.ITEMS));
        // 同一目标字段不会被重复映射
        assertEquals(targets.size(), targets.stream().distinct().count());
    }

    @Test
    void shouldMapAmazonStyleRowToStandardRow() {
        List<OrderFieldMapping> mappings = service.autoDetect(service.detectSourceFields(List.of(amazonRow())));
        OrderFieldMappingService.MappingResult result = service.mapRow(mappings, amazonRow(), 1);
        ImportedOrderRow row = result.row();

        assertEquals("AMZ-001", row.externalOrderId());
        assertEquals("张三", row.customerName());
        assertEquals(OrderStatus.PENDING, row.status());
        assertEquals(0, new BigDecimal("1299.00").compareTo(row.totalAmount()));
        assertEquals(2, row.items().size());
        assertEquals("SKU-001", row.items().get(0).sku());
        assertEquals(2, row.items().get(0).quantity());
        assertEquals(0, new BigDecimal("198.0").compareTo(row.items().get(0).totalPrice()));
        assertEquals("李四", row.recipient());
        // 未映射字段不阻断导入
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void shouldCollectRowLevelErrorsInsteadOfThrowing() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("amazon-order-id", "AMZ-002");
        row.put("order-total", "not-a-number");

        List<OrderFieldMapping> mappings =
                service.autoDetect(service.detectSourceFields(List.of(row)));
        OrderFieldMappingService.MappingResult result = service.mapRow(mappings, row, 3);

        List<String> errorFields = result.errors().stream()
                .map(OrderImportError::getField)
                .toList();
        assertTrue(errorFields.contains(OrderImportTargetField.SHIPPING_RECIPIENT.name()));
        assertTrue(errorFields.contains(OrderImportTargetField.SHIPPING_STREET.name()));
        assertTrue(errorFields.contains(OrderImportTargetField.ITEMS.name()));
        assertTrue(errorFields.contains(OrderImportTargetField.TOTAL_AMOUNT.name()));
    }

    @Test
    void shouldFallbackToFlatSingleItemWhenNoNestedItems() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("externalOrderId", "GEN-001");
        row.put("recipientName", "王五");
        row.put("street", "中山路1号");
        row.put("sku", "SKU-100");
        row.put("productName", "通用商品");
        row.put("quantity", "3");
        row.put("unitPrice", "10.50");

        List<OrderFieldMapping> mappings = service.autoDetect(service.detectSourceFields(List.of(row)));
        OrderFieldMappingService.MappingResult result = service.mapRow(mappings, row, 1);

        List<ImportedOrderItem> items = result.row().items();
        assertEquals(1, items.size());
        assertEquals("SKU-100", items.get(0).sku());
        assertEquals(3, items.get(0).quantity());
        assertEquals(0, new BigDecimal("31.50").compareTo(result.row().itemsTotal()));
    }

    @Test
    void shouldParseOrderStatusAndNumbers() {
        assertEquals(OrderStatus.SHIPPED, service.toOrderStatus("Shipped"));
        assertEquals(OrderStatus.COMPLETED, service.toOrderStatus("delivered"));
        assertEquals(OrderStatus.CANCELLED, service.toOrderStatus("cancelled"));
        assertEquals(OrderStatus.PENDING, service.toOrderStatus("whatever"));
        assertNotNull(service.toDateTime("2026-09-01 10:00:00"));
        assertNotNull(service.toDateTime("2026-09-01"));
    }
}
