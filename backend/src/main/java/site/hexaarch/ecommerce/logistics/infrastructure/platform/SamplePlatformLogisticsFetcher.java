package site.hexaarch.ecommerce.logistics.infrastructure.platform;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformLogisticsFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformLogisticsFetcher;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台物流数据示例适配器。
 * <p>
 * 用途：在尚未完成真实平台物流接口联调、或需要演示/自测「物流数据获取 -> 字段映射 -> 批量导入 -> 结果反馈」
 * 链路时使用；同时它也是HTTP适配器的降级实现。
 * 示例行的 {@code externalOrderId} 与 {@link SamplePlatformOrderFetcher} 保持一致，便于物流单绑定到已导入的订单。
 * </p>
 *
 * @author kenyon
 */
@Slf4j
@Component
public class SamplePlatformLogisticsFetcher implements PlatformLogisticsFetcher {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String[] CARRIERS = {"顺丰速运", "中通快递", "京东物流", "USPS", "DHL"};
    private static final String[] CHANNEL_CODES = {"SF", "ZTO", "JD", "USPS", "DHL"};
    private static final String[] STATUSES = {"Shipped", "InTransit", "Delivered"};

    @Override
    public String type() {
        return "SAMPLE";
    }

    @Override
    public List<Map<String, Object>> fetch(PlatformLogisticsFetchRequest request) {
        int limit = request.limit() == null || request.limit() <= 0 ? 5 : Math.min(request.limit(), 50);
        String platform = request.platform() == null ? "OTHER" : request.platform().toUpperCase();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i <= limit; i++) {
            rows.add(buildRow(platform, i));
        }
        log.info("[PlatformLogisticsFetch] 使用示例数据 platform={} rows={}", platform, rows.size());
        return rows;
    }

    private Map<String, Object> buildRow(String platform, int index) {
        String suffix = String.format("%03d", index);
        int carrierIndex = index % CARRIERS.length;
        LocalDateTime shippedAt = LocalDateTime.now().minusDays(index);
        Map<String, Object> row = new LinkedHashMap<>();
        switch (platform) {
            case "AMAZON" -> {
                row.put("shipment-id", platform + "-SHIP-" + suffix);
                row.put("amazon-order-id", platform + "-ORD-" + suffix);
                row.put("tracking-number", platform.substring(0, 1) + suffix + "-TN-" + index);
                row.put("carrier-name", CARRIERS[carrierIndex]);
                row.put("shipping-status", STATUSES[index % STATUSES.length]);
                row.put("ship-date", shippedAt.format(FORMATTER));
                row.put("estimated-delivery-date", shippedAt.plusDays(5).format(FORMATTER));
                row.put("delivered-date", index % 3 == 2 ? shippedAt.plusDays(4).format(FORMATTER) : null);
                row.put("shipping-url", "https://track.example.com/" + platform + "/" + suffix);
                row.put("recipient-name", "张三" + index);
                row.put("phone", "1380013" + String.format("%04d", index));
                row.put("country", "中国");
                row.put("state", "广东省");
                row.put("city", "深圳市");
                row.put("address-line1", "南山区科技园路 " + index + " 号");
                row.put("postal-code", "518000");
                row.put("shipping-fee", 12.5 + index);
                row.put("currency", "CNY");
                // 亚马逊以 JSON 字符串回传轨迹，导入链路需要兼容该写法
                row.put("event-history", "[{\"time\":\"" + shippedAt.format(FORMATTER) + "\",\"status\":\"Shipped\","
                        + "\"description\":\"已揽收\",\"location\":\"深圳转运中心\"}]");
            }
            case "SHOPIFY", "MAGENTO" -> {
                row.put("fulfillmentId", platform + "-SHIP-" + suffix);
                row.put("orderId", platform + "-ORD-" + suffix);
                row.put("trackingNumber", platform.substring(0, 1) + suffix + "-TN-" + index);
                row.put("trackingCompany", CARRIERS[carrierIndex]);
                row.put("channelCode", CHANNEL_CODES[carrierIndex]);
                row.put("status", STATUSES[index % STATUSES.length]);
                row.put("shippedAt", shippedAt.format(FORMATTER));
                row.put("estimatedArrivalAt", shippedAt.plusDays(3).format(FORMATTER));
                row.put("trackingUrl", "https://track.example.com/" + platform + "/" + suffix);
                row.put("labelUrl", "https://labels.example.com/" + platform + "/" + suffix + ".pdf");
                row.put("labelFormat", "PDF");
                row.put("barcode", "BC" + suffix + index);
                row.put("recipientName", "李四" + index);
                row.put("phoneNumber", "1390013" + String.format("%04d", index));
                row.put("country", "中国");
                row.put("province", "浙江省");
                row.put("city", "杭州市");
                row.put("addressLine1", "西湖区文三路 " + index + " 号");
                row.put("zipCode", "310000");
                row.put("shippingFee", 9.9 + index);
                row.put("currency", "CNY");
                // 结构化数组轨迹
                row.put("traces", List.of(
                        trace(shippedAt, "Shipped", "已发货", "杭州仓库"),
                        trace(shippedAt.plusHours(6), "InTransit", "运输中", "杭州转运中心")));
            }
            case "EBAY", "ALIEXPRESS", "WISH" -> {
                row.put("logisticsId", platform + "-SHIP-" + suffix);
                row.put("orderNumber", platform + "-ORD-" + suffix);
                row.put("trackingNo", platform.substring(0, 1) + suffix + "-TN-" + index);
                row.put("logisticsCompany", CARRIERS[carrierIndex]);
                row.put("logisticsStatus", STATUSES[index % STATUSES.length]);
                row.put("sendTime", shippedAt.format(FORMATTER));
                row.put("signTime", index % 3 == 2 ? shippedAt.plusDays(6).format(FORMATTER) : null);
                row.put("freight", 15.0 + index);
                row.put("currency", "USD");
                row.put("receiverName", "王五" + index);
                row.put("mobile", "1370013" + String.format("%04d", index));
                row.put("country", "United States");
                row.put("stateOrProvince", "California");
                row.put("city", "Los Angeles");
                row.put("streetAddress", "Broadway " + index);
                row.put("postalCode", "90001");
            }
            default -> {
                row.put("externalLogisticsId", platform + "-SHIP-" + suffix);
                row.put("externalOrderId", platform + "-ORD-" + suffix);
                row.put("trackingNumber", platform.substring(0, 1) + suffix + "-TN-" + index);
                row.put("carrier", CARRIERS[carrierIndex]);
                row.put("channelCode", CHANNEL_CODES[carrierIndex]);
                row.put("status", STATUSES[index % STATUSES.length]);
                row.put("shippedAt", shippedAt.format(FORMATTER));
                row.put("estimatedArrivalAt", shippedAt.plusDays(4).format(FORMATTER));
                row.put("totalFee", 8.8 + index);
                row.put("currency", "CNY");
                row.put("recipient", "赵六" + index);
                row.put("phone", "1360013" + String.format("%04d", index));
                row.put("country", "中国");
                row.put("province", "北京市");
                row.put("city", "北京市");
                row.put("street", "朝阳区建国路 " + index + " 号");
                row.put("zipCode", "100022");
                row.put("traceTime", shippedAt.format(FORMATTER));
                row.put("traceDescription", "示例轨迹：包裹已发出");
                row.put("traceLocation", "北京集散中心");
            }
        }
        // 故意保留一行异常数据（缺少平台物流单号），用于验证导入过程的行级异常处理
        if (index == 2) {
            row.remove("shipment-id");
            row.remove("fulfillmentId");
            row.remove("logisticsId");
            row.remove("externalLogisticsId");
        }
        return row;
    }

    private Map<String, Object> trace(LocalDateTime time, String status, String description, String location) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("time", time.format(FORMATTER));
        trace.put("status", status);
        trace.put("description", description);
        trace.put("location", location);
        return trace;
    }
}
