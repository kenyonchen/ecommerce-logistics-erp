package site.hexaarch.ecommerce.logistics.domain.common.exception;

/**
 * 无效操作异常，用于表示尝试执行无效操作时抛出的异常
 *
 * @author kenyon
 */
public class InvalidOperationException extends BusinessException {
    public InvalidOperationException(String message) {
        super(message, "INVALID_OPERATION");
    }

    public InvalidOperationException(String message, Throwable cause) {
        super(message, "INVALID_OPERATION", cause);
    }
}
