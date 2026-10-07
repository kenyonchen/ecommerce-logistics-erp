package site.hexaarch.ecommerce.logistics.domain.order.entity;

/**
 * 订单导入任务中的单条错误明细（行级异常）。
 *
 * @author kenyon
 */
public class OrderImportError {
    private Integer rowNumber;
    private String externalOrderId;
    private String orderNumber;
    private String field;
    private String message;

    public static OrderImportError of(Integer rowNumber, String externalOrderId, String orderNumber,
                                      String field, String message) {
        OrderImportError error = new OrderImportError();
        error.rowNumber = rowNumber;
        error.externalOrderId = externalOrderId;
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

    public String getExternalOrderId() {
        return externalOrderId;
    }

    public void setExternalOrderId(String externalOrderId) {
        this.externalOrderId = externalOrderId;
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
