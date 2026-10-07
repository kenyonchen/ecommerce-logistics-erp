package site.hexaarch.ecommerce.logistics.domain.customer.entity;

/**
 * 客户导入任务中的单条错误明细（行级异常）。
 *
 * @author kenyon
 */
public class CustomerImportError {
    private Integer rowNumber;
    private String externalBuyerId;
    private String customerName;
    private String field;
    private String message;

    public static CustomerImportError of(Integer rowNumber, String externalBuyerId, String customerName,
                                         String field, String message) {
        CustomerImportError error = new CustomerImportError();
        error.rowNumber = rowNumber;
        error.externalBuyerId = externalBuyerId;
        error.customerName = customerName;
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

    public String getExternalBuyerId() {
        return externalBuyerId;
    }

    public void setExternalBuyerId(String externalBuyerId) {
        this.externalBuyerId = externalBuyerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
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
