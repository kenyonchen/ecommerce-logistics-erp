package site.hexaarch.ecommerce.logistics.domain.platform.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存同步到平台的结果。
 *
 * @param integrationId 平台集成ID
 * @param platformName  平台名称
 * @param storeUrl      店铺地址
 * @param pushedItems   推送的SKU明细（sku / quantity）
 * @param pushedAt      推送时间
 * @author kenyon
 */
public record InventorySyncResult(String integrationId,
                                  String platformName,
                                  String storeUrl,
                                  List<SkuQuantity> pushedItems,
                                  LocalDateTime pushedAt) {

    /**
     * SKU库存对
     */
    public record SkuQuantity(String sku, int quantity) {
    }

    public int pushedCount() {
        return pushedItems == null ? 0 : pushedItems.size();
    }
}
