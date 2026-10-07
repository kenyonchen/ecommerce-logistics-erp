package site.hexaarch.ecommerce.logistics.infrastructure.platform;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformWarehouseFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformWarehouseFetcher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台店铺仓库示例数据适配器。
 * <p>
 * 用途：在尚未完成真实平台联调、或需要演示/自测店铺仓库对接链路时使用；
 * 同时它也是HTTP适配器的降级实现，保证「拉取 -> 字段映射 -> 批量导入 -> 结果反馈」链路始终可跑通。
 * </p>
 *
 * @author kenyon
 */
@Slf4j
@Component
public class SamplePlatformWarehouseFetcher implements PlatformWarehouseFetcher {

    @Override
    public String type() {
        return "SAMPLE";
    }

    @Override
    public List<Map<String, Object>> fetch(PlatformWarehouseFetchRequest request) {
        int limit = request.limit() == null || request.limit() <= 0 ? 4 : Math.min(request.limit(), 50);
        String platform = request.platform() == null ? "OTHER" : request.platform().toUpperCase();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i <= limit; i++) {
            rows.add(buildRow(platform, i));
        }
        log.info("[PlatformWarehouseFetch] 使用示例数据 platform={} rows={}", platform, rows.size());
        return rows;
    }

    private Map<String, Object> buildRow(String platform, int index) {
        String suffix = String.format("%02d", index);
        Map<String, Object> row = new LinkedHashMap<>();
        switch (platform) {
            case "AMAZON" -> {
                row.put("shop_code", "AMZ-STORE-" + suffix);
                row.put("shop_name", "亚马逊店铺" + index);
                row.put("platform", "AMAZON");
                row.put("warehouse_id", "AMZ-WH-" + suffix);
                row.put("warehouse_name", "Amazon FBA 仓" + index);
                row.put("address", "美国加州" + index + "号仓");
                row.put("is_default", index == 1 ? "true" : "false");
                row.put("status", "Active");
            }
            case "SHOPIFY", "MAGENTO" -> {
                row.put("shopCode", "SHOP-STORE-" + suffix);
                row.put("shopName", "Shopify店铺" + index);
                row.put("platform", platform);
                row.put("platformWarehouseId", "SHOP-WH-" + suffix);
                row.put("platformWarehouseName", "Shopify仓" + index);
                row.put("address", "加拿大多伦多" + index + "号仓");
                row.put("isDefault", index == 1);
                row.put("active", true);
            }
            case "EBAY", "ALIEXPRESS", "WISH" -> {
                row.put("shopCode", "EBAY-STORE-" + suffix);
                row.put("shopName", "eBay店铺" + index);
                row.put("platform", platform);
                row.put("warehouseId", "EBAY-WH-" + suffix);
                row.put("name", "海外仓" + index);
                row.put("address", "德国柏林" + index + "号仓");
                row.put("active", "yes");
            }
            default -> {
                row.put("shopCode", "GEN-STORE-" + suffix);
                row.put("shopName", "通用店铺" + index);
                row.put("platform", platform);
                row.put("warehouseCode", "GEN-WH-" + suffix);
                row.put("warehouseName", "通用仓库" + index);
                row.put("address", "中国深圳" + index + "号仓");
                row.put("isDefault", index == 1);
                row.put("active", true);
            }
        }
        // 故意保留一行异常数据（缺少平台仓库ID），用于验证导入过程的行级异常处理
        if (index == 2) {
            row.remove("warehouse_id");
            row.remove("platformWarehouseId");
            row.remove("warehouseId");
            row.remove("warehouseCode");
        }
        return row;
    }
}
