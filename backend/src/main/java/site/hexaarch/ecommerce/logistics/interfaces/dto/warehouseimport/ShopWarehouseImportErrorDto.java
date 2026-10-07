package site.hexaarch.ecommerce.logistics.interfaces.dto.warehouseimport;

import lombok.Data;

/**
 * 店铺仓库导入行错误明细DTO
 *
 * @author kenyon
 */
@Data
public class ShopWarehouseImportErrorDto {
    private Integer rowNumber;
    private String shopCode;
    private String platformWarehouseId;
    private String field;
    private String message;
}
