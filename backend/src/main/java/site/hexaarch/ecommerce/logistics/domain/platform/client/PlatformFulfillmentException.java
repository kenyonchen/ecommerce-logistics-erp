package site.hexaarch.ecommerce.logistics.domain.platform.client;

/**
 * 平台发货回写（outbound）失败。
 *
 * @author kenyon
 */
public class PlatformFulfillmentException extends RuntimeException {

    public PlatformFulfillmentException(String message) {
        super(message);
    }

    public PlatformFulfillmentException(String message, Throwable cause) {
        super(message, cause);
    }
}
