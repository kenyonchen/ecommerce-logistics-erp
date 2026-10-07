package site.hexaarch.ecommerce.logistics.interfaces.dto.productimport;

import lombok.Data;

/**
 * 导入行错误明细DTO
 *
 * @author kenyon
 */
@Data
public class ProductImportErrorDto {
    private Integer rowNumber;
    private String externalProductId;
    private String skuCode;
    private String field;
    private String message;
}
