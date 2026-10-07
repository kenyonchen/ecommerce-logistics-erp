package site.hexaarch.ecommerce.logistics.domain.logistics.entity;

/**
 * 物流导入任务中的单条错误明细（行级异常）。
 *
 * @author kenyon
 */
public class LogisticsImportError {
    private Integer rowNumber;
    private String externalLogisticsId;
    private String orderNumber;
    private String field;
    private String message;

    public static LogisticsImportError of(Integer rowNumber, String externalLogisticsId, String orderNumber,
                                          String field, String message) {
        LogisticsImportError error = new LogisticsImportError();
        error.rowNumber = rowNumber;
        error.externalLogisticsId = externalLogisticsId;
        error.orderNumber = orderNumber;
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

    public String getExternalLogisticsId() {
        return externalLogisticsId;
    }

    public void setExternalLogisticsId(String externalLogisticsId) {
        this.externalLogisticsId = externalLogisticsId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
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
