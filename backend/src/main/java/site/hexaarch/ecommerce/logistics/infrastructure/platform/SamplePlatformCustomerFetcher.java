package site.hexaarch.ecommerce.logistics.infrastructure.platform;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCustomerFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCustomerFetcher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台客户（买家）示例数据适配器。
 * <p>
 * 用途：在尚未完成真实平台联调、或需要演示/自测导入链路时使用；
 * 同时它也是HTTP适配器的降级实现，保证「拉取 -> 字段映射 -> 批量导入 -> 结果反馈」链路始终可跑通。
 * </p>
 *
 * @author kenyon
 */
@Slf4j
@Component("samplePlatformCustomerFetcher")
public class SamplePlatformCustomerFetcher implements PlatformCustomerFetcher {

    @Override
    public String type() {
        return "SAMPLE";
    }

    @Override
    public List<Map<String, Object>> fetch(PlatformCustomerFetchRequest request) {
        int limit = request.limit() == null || request.limit() <= 0 ? 5 : Math.min(request.limit(), 50);
        String platform = request.platform() == null ? "OTHER" : request.platform().toUpperCase();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i <= limit; i++) {
            rows.add(buildRow(platform, i));
        }
        log.info("[PlatformCustomerFetch] 使用示例数据 platform={} rows={}", platform, rows.size());
        return rows;
    }

    private Map<String, Object> buildRow(String platform, int index) {
        String suffix = String.format("%03d", index);
        Map<String, Object> row = new LinkedHashMap<>();
        switch (platform) {
            // 亚马逊不回传买家电话与地址（PiiSpi 限制），仅能拿到买家ID/姓名/邮箱
            case "AMAZON" -> {
                row.put("buyer_id", "amz-buyer-" + suffix);
                row.put("buyer-name", "示例买家A" + index);
                row.put("buyer-email", "buyer" + index + "@example.com");
            }
            // Shopify/Magento 客户接口不提供买家外部ID，演示以邮箱兜底去重的路径
            case "SHOPIFY", "MAGENTO" -> {
                row.put("customerName", "示例买家S" + index);
                row.put("email", "shopper" + index + "@example.com");
                row.put("phoneNumber", "1390013" + String.format("%04d", index));
                row.put("country", "中国");
                row.put("province", "浙江省");
                row.put("city", "杭州市");
                row.put("addressLine1", "西湖区文三路 " + index + " 号");
                row.put("zipCode", "310000");
            }
            case "EBAY", "ALIEXPRESS", "WISH" -> {
                row.put("buyerId", "buyer-" + suffix);
                row.put("buyerName", "示例买家E" + index);
                row.put("buyerEmail", "ebay" + index + "@example.com");
                row.put("phone", "1370013" + String.format("%04d", index));
                row.put("country", "中国");
                row.put("state", "江苏省");
                row.put("city", "南京市");
                row.put("streetAddress", "鼓楼区中山路 " + index + " 号");
                row.put("postalCode", "210000");
                if (index % 2 == 0) {
                    row.put("company", "示例公司E" + index);
                }
            }
            default -> {
                row.put("customerName", "通用示例买家" + index);
                row.put("customerEmail", "demo" + index + "@example.com");
                row.put("phone", "1360013" + String.format("%04d", index));
                row.put("address", "朝阳区建国路 " + index + " 号");
                row.put("country", "中国");
                row.put("province", "北京市");
                row.put("city", "北京市");
            }
        }
        // 故意让 index==3 的行缺失邮箱，用于验证导入过程的行级容错
        if (index == 3) {
            row.remove("buyer-email");
            row.remove("email");
            row.remove("buyerEmail");
            row.remove("customerEmail");
        }
        return row;
    }
}
