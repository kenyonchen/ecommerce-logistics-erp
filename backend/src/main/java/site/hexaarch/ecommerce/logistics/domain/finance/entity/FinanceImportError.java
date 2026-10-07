package site.hexaarch.ecommerce.logistics.domain.finance.entity;

/**
 * 财务导入任务中的单条错误明细（行级异常）。
 *
 * @author kenyon
 */
public class FinanceImportError {
    private Integer rowNumber;
    private String externalTransactionId;
    private String field;
    private String message;

    public static FinanceImportError of(Integer rowNumber, String externalTransactionId,
                                        String field, String message) {
        FinanceImportError error = new FinanceImportError();
        error.rowNumber = rowNumber;
        error.externalTransactionId = externalTransactionId;
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

    public String getExternalTransactionId() {
        return externalTransactionId;
    }

    public void setExternalTransactionId(String externalTransactionId) {
        this.externalTransactionId = externalTransactionId;
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
