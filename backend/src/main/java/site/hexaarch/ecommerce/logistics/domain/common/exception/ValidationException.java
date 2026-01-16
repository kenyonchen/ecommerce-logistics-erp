package site.hexaarch.ecommerce.logistics.domain.common.exception;

/**
 * 验证异常，用于表示输入参数验证失败时抛出的异常
 *
 * @author kenyon
 */
public class ValidationException extends BusinessException {
    public ValidationException(String message) {
        super(message, "VALIDATION_ERROR");
    }

    public ValidationException(String message, Throwable cause) {
        super(message, "VALIDATION_ERROR", cause);
    }
}
