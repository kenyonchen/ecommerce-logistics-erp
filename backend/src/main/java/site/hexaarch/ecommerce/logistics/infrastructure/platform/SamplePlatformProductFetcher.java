package site.hexaarch.ecommerce.logistics.infrastructure.platform;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformProductFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformProductFetcher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台商品示例数据适配器。
 * <p>
 * 用途：在尚未完成真实平台联调、或需要演示/自测导入链路时使用；
 * 同时它也是HTTP适配器的降级实现，保证「拉取 -> 字段映射 -> 批量导入 -> 结果反馈」链路始终可跑通。
 * </p>
 *
 * @author kenyon
 */
@Slf4j
@Component
public class SamplePlatformProductFetcher implements PlatformProductFetcher {

    @Override
    public String type() {
        return "SAMPLE";
    }

    @Override
    public List<Map<String, Object>> fetch(PlatformProductFetchRequest request) {
        int limit = request.limit() == null || request.limit() <= 0 ? 5 : Math.min(request.limit(), 50);
        String platform = request.platform() == null ? "OTHER" : request.platform().toUpperCase();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i <= limit; i++) {
            rows.add(buildRow(platform, i));
        }
        log.info("[PlatformFetch] 使用示例数据 platform={} rows={}", platform, rows.size());
        return rows;
    }

    private Map<String, Object> buildRow(String platform, int index) {
        String suffix = String.format("%03d", index);
        String externalId = platform + "-EXT-" + suffix;
        String skuCode = platform + "-SKU-" + suffix;

        Map<String, Object> row = new LinkedHashMap<>();
        switch (platform) {
            case "AMAZON" -> {
                row.put("external_id", externalId);
                row.put("item-name", "示例商品A" + index);
                row.put("seller-sku", skuCode);
                row.put("product_description", "Amazon 平台同步的示例商品 " + index);
                row.put("product_type", "ELECTRONICS");
                row.put("price", 199.0 + index);
                row.put("cost", 120.0 + index);
                row.put("currency", "CNY");
                row.put("item_weight", 0.5 + index);
                row.put("variation_attributes", "颜色:黑色,容量:128GB");
                row.put("status", "Active");
            }
            case "SHOPIFY", "MAGENTO" -> {
                row.put("external_id", externalId);
                row.put("title", "示例商品S" + index);
                row.put("sku", skuCode);
                row.put("description", "Shopify 平台同步的示例商品 " + index);
                row.put("product_type", "HOME");
                row.put("price", 89.0 + index);
                row.put("currency", "CNY");
                row.put("weight", 300 + index * 10);
                row.put("attributes", "颜色:白色");
                row.put("active", true);
            }
            case "EBAY", "ALIEXPRESS", "WISH" -> {
                row.put("external_id", externalId);
                row.put("title", "示例商品E" + index);
                row.put("sku", skuCode);
                row.put("description", "eBay/速卖通 示例商品 " + index);
                row.put("category", "CLOTHING");
                row.put("price", 59.0 + index);
                row.put("currency", "CNY");
                row.put("weight", 250 + index * 5);
                row.put("active", "yes");
            }
            default -> {
                row.put("externalProductId", externalId);
                row.put("name", "通用示例商品" + index);
                row.put("skuCode", skuCode);
                row.put("description", "通用示例商品描述 " + index);
                row.put("categoryName", "OTHER");
                row.put("sellingPrice", 99.0 + index);
                row.put("costPrice", 60.0 + index);
                row.put("currency", "CNY");
                row.put("weightInGrams", 400 + index * 20);
                row.put("attributes", "颜色:黑色");
                row.put("active", true);
            }
        }
        // 故意保留一行异常数据，用于验证导入过程的行级异常处理
        if (index == 2) {
            row.remove(detectNameKey(row));
        }
        return row;
    }

    private String detectNameKey(Map<String, Object> row) {
        for (String candidate : List.of("item-name", "title", "name")) {
            if (row.containsKey(candidate)) {
                return candidate;
            }
        }
        return "name";
    }
}
