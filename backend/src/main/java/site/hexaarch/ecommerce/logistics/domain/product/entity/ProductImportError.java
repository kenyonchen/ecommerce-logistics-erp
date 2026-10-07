package site.hexaarch.ecommerce.logistics.domain.product.entity;

/**
 * 商品导入任务中的单条错误明细（行级异常）。
 *
 * @author kenyon
 */
public class ProductImportError {
    private Integer rowNumber;
    private String externalProductId;
    private String skuCode;
    private String field;
    private String message;

    public static ProductImportError of(Integer rowNumber, String externalProductId, String skuCode,
                                        String field, String message) {
        ProductImportError error = new ProductImportError();
        error.rowNumber = rowNumber;
        error.externalProductId = externalProductId;
        error.skuCode = skuCode;
        error.field = field;
        error.message = message;
        return error;
    }

    public Integer getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(Integer rowNumber) {
        this.rowNumber = rowNumber;
    }

    public String getExternalProductId() {
        return externalProductId;
    }

    public void setExternalProductId(String externalProductId) {
        this.externalProductId = externalProductId;
    }

    public String getSkuCode() {
        return skuCode;
    }

    public void setSkuCode(String skuCode) {
        this.skuCode = skuCode;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
