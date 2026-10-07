package site.hexaarch.ecommerce.logistics.interfaces.dto.productimport;

import lombok.Data;
import site.hexaarch.ecommerce.logistics.interfaces.dto.product.ProductAttributeDto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 字段映射后的标准商品导入行，前后端在「导入预览」与「批量导入」环节使用同一结构。
 *
 * @author kenyon
 */
@Data
public class ProductImportRowDto {
    private Integer rowNumber;
    private String externalProductId;
    private String externalSkuId;
    private String name;
    private String description;
    private String categoryName;
    private String skuCode;
    private String skuName;
    private BigDecimal sellingPrice;
    private BigDecimal costPrice;
    private String currency;
    private Integer weightInGrams;
    private Integer volumeInCubicCentimeters;
    private List<ProductAttributeDto> attributes;
    private Boolean active;
    /**
     * 行级错误（预览阶段即可暴露数据问题）
     */
    private List<ProductImportErrorDto> errors;
}
