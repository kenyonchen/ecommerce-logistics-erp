package site.hexaarch.ecommerce.logistics.domain.warehouse.entity;

/**
 * 店铺仓库导入任务中的单条错误明细（行级异常）。
 *
 * @author kenyon
 */
public class ShopWarehouseImportError {
    private Integer rowNumber;
    private String shopCode;
    private String platformWarehouseId;
    private String field;
    private String message;

    public static ShopWarehouseImportError of(Integer rowNumber, String shopCode, String platformWarehouseId,
                                              String field, String message) {
        ShopWarehouseImportError error = new ShopWarehouseImportError();
        error.rowNumber = rowNumber;
        error.shopCode = shopCode;
        error.platformWarehouseId = platformWarehouseId;
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

    public String getShopCode() {
        return shopCode;
    }

    public void setShopCode(String shopCode) {
        this.shopCode = shopCode;
    }

    public String getPlatformWarehouseId() {
        return platformWarehouseId;
    }

    public void setPlatformWarehouseId(String platformWarehouseId) {
        this.platformWarehouseId = platformWarehouseId;
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
