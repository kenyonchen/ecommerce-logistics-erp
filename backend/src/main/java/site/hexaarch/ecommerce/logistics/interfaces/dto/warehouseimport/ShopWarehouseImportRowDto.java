package site.hexaarch.ecommerce.logistics.interfaces.dto.warehouseimport;

import lombok.Data;

/**
 * 字段映射后的标准店铺仓库导入行，前后端在「导入预览」与「批量导入」环节使用同一结构。
 *
 * @author kenyon
 */
@Data
public class ShopWarehouseImportRowDto {
    private Integer rowNumber;
    private String shopCode;
    private String shopName;
    private String platform;
    private String platformWarehouseId;
    private String platformWarehouseName;
    private String warehouseCode;
    private String address;
    private Boolean isDefault;
    private Boolean active;
    /**
     * 行级错误（预览阶段即可暴露数据问题）
     */
    private java.util.List<ShopWarehouseImportErrorDto> errors;
}
