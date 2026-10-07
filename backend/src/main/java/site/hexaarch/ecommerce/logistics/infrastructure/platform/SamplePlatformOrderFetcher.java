package site.hexaarch.ecommerce.logistics.infrastructure.platform;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformOrderFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformOrderFetcher;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台订单示例数据适配器。
 * <p>
 * 用途：在尚未完成真实平台联调、或需要演示/自测导入链路时使用；
 * 同时它也是HTTP适配器的降级实现，保证「拉取 -> 字段映射 -> 批量导入 -> 结果反馈」链路始终可跑通。
 * </p>
 *
 * @author kenyon
 */
@Slf4j
@Component
public class SamplePlatformOrderFetcher implements PlatformOrderFetcher {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String[] STATUSES = {"Pending", "Processing", "Shipped"};

    @Override
    public String type() {
        return "SAMPLE";
    }

    @Override
    public List<Map<String, Object>> fetch(PlatformOrderFetchRequest request) {
        int limit = request.limit() == null || request.limit() <= 0 ? 5 : Math.min(request.limit(), 50);
        String platform = request.platform() == null ? "OTHER" : request.platform().toUpperCase();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i <= limit; i++) {
            rows.add(buildRow(platform, i));
        }
        log.info("[PlatformOrderFetch] 使用示例数据 platform={} rows={}", platform, rows.size());
        return rows;
    }

    private Map<String, Object> buildRow(String platform, int index) {
        String suffix = String.format("%03d", index);
        String externalOrderId = platform + "-ORD-" + suffix;

        Map<String, Object> row = new LinkedHashMap<>();
        switch (platform) {
            case "AMAZON" -> {
                row.put("amazon-order-id", externalOrderId);
                row.put("buyer-name", "示例买家A" + index);
                row.put("buyer-email", "buyer" + index + "@example.com");
                row.put("order-status", STATUSES[index % STATUSES.length]);
                row.put("purchase-date", LocalDateTime.now().minusDays(index).format(FORMATTER));
                row.put("payment-method", "Standard");
                row.put("payment-status", "PAID");
                row.put("transaction-id", "TXN-" + platform + "-" + suffix);
                row.put("order-total", 199.0 + index * 10);
                row.put("currency", "CNY");
                row.put("recipient-name", "张三" + index);
                row.put("phone", "1380013" + String.format("%04d", index));
                row.put("country", "中国");
                row.put("state", "广东省");
                row.put("city", "深圳市");
                row.put("address-line1", "南山区科技园路 " + index + " 号");
                row.put("postal-code", "518000");
                row.put("items", List.of(
                        item(platform + "-SKU-" + suffix, "示例商品A" + index, 1 + index % 3, 99.0 + index),
                        item(platform + "-SKU-B" + suffix, "示例赠品" + index, 1, 0.0)));
            }
            case "SHOPIFY", "MAGENTO" -> {
                row.put("orderId", externalOrderId);
                row.put("customerName", "示例买家S" + index);
                row.put("email", "shopper" + index + "@example.com");
                row.put("status", STATUSES[index % STATUSES.length]);
                row.put("createdAt", LocalDateTime.now().minusDays(index).format(FORMATTER));
                row.put("paymentMethod", "CREDIT_CARD");
                row.put("paymentStatus", "PAID");
                row.put("transactionId", "TXN-" + platform + "-" + suffix);
                row.put("totalAmount", 289.0 + index * 10);
                row.put("currency", "CNY");
                row.put("recipientName", "李四" + index);
                row.put("phoneNumber", "1390013" + String.format("%04d", index));
                row.put("country", "中国");
                row.put("province", "浙江省");
                row.put("city", "杭州市");
                row.put("addressLine1", "西湖区文三路 " + index + " 号");
                row.put("zipCode", "310000");
                row.put("lineItems", List.of(
                        item(platform + "-SKU-" + suffix, "示例商品S" + index, 2, 120.0 + index)));
            }
            case "EBAY", "ALIEXPRESS", "WISH" -> {
                row.put("orderNumber", externalOrderId);
                row.put("buyerId", "buyer-" + suffix);
                row.put("buyerName", "示例买家E" + index);
                row.put("buyerEmail", "ebay" + index + "@example.com");
                row.put("orderStatus", STATUSES[index % STATUSES.length]);
                row.put("orderTime", LocalDateTime.now().minusDays(index).format(FORMATTER));
                row.put("paymentMethod", "PAYPAL");
                row.put("paymentStatus", "PAID");
                row.put("transactionId", "TXN-" + platform + "-" + suffix);
                row.put("orderTotal", 159.0 + index * 10);
                row.put("currency", "CNY");
                row.put("recipientName", "王五" + index);
                row.put("phone", "1370013" + String.format("%04d", index));
                row.put("country", "中国");
                row.put("stateOrProvince", "江苏省");
                row.put("city", "南京市");
                row.put("streetAddress", "鼓楼区中山路 " + index + " 号");
                row.put("postalCode", "210000");
                row.put("items", List.of(
                        item(platform + "-SKU-" + suffix, "示例商品E" + index, 1, 159.0 + index)));
            }
            default -> {
                row.put("externalOrderId", externalOrderId);
                row.put("customerName", "通用示例买家" + index);
                row.put("customerEmail", "demo" + index + "@example.com");
                row.put("orderStatus", STATUSES[index % STATUSES.length]);
                row.put("orderTime", LocalDateTime.now().minusDays(index).format(FORMATTER));
                row.put("paymentMethod", "BANK_TRANSFER");
                row.put("paymentStatus", "PAID");
                row.put("transactionId", "TXN-" + platform + "-" + suffix);
                row.put("totalAmount", 99.0 + index * 10);
                row.put("currency", "CNY");
                row.put("recipientName", "赵六" + index);
                row.put("phone", "1360013" + String.format("%04d", index));
                row.put("country", "中国");
                row.put("province", "北京市");
                row.put("city", "北京市");
                row.put("street", "朝阳区建国路 " + index + " 号");
                row.put("zipCode", "100022");
                row.put("items", List.of(
                        item(platform + "-SKU-" + suffix, "通用示例商品" + index, 1, 99.0 + index)));
            }
        }
        // 故意保留一行异常数据，用于验证导入过程的行级异常处理
        if (index == 2) {
            row.remove("recipient-name");
            row.remove("recipientName");
            row.remove("street");
        }
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
}
