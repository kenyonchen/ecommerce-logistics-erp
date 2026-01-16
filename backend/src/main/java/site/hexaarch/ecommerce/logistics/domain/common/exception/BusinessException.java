package site.hexaarch.ecommerce.logistics.domain.common.exception;

/**
 * 业务异常基类，用于表示业务逻辑中出现的异常情况
 *
 * @author kenyon
 */
public class BusinessException extends RuntimeException {
    private final String errorCode;
    private final Object[] params;

    /**
     * 构造方法
     *
     * @param message   错误消息
     * @param errorCode 错误码
     */
    public BusinessException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.params = null;
    }

    /**
     * 构造方法
     *
     * @param message   错误消息
     * @param errorCode 错误码
     * @param cause     原始异常
     */
    public BusinessException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.params = null;
    }

    /**
     * 构造方法
     *
     * @param message   错误消息
     * @param errorCode 错误码
     * @param params    错误消息参数
     */
    public BusinessException(String message, String errorCode, Object... params) {
        super(message);
        this.errorCode = errorCode;
        this.params = params;
    }

    /**
     * 获取错误码
     *
     * @return 错误码
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * 获取错误消息参数
     *
     * @return 错误消息参数
     */
    public Object[] getParams() {
        return params;
    }
}
