package site.hexaarch.ecommerce.logistics.domain.warehouse.valueobject;

/**
 * 经过字段映射后的标准化店铺仓库行，是第三方平台数据进入领域模型之前的中间结构。
 *
 * @author kenyon
 */
public record ImportedShopWarehouseRow(
        int rowNumber,
        String shopCode,
        String shopName,
        String platform,
        String platformWarehouseId,
        String platformWarehouseName,
        String warehouseCode,
        String address,
        Boolean isDefault,
        Boolean active
) {
}
