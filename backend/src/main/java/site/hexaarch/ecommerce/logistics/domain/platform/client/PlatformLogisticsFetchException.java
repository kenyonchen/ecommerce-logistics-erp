package site.hexaarch.ecommerce.logistics.domain.platform.client;

/**
 * 拉取平台物流数据失败异常。
 *
 * @author kenyon
 */
public class PlatformLogisticsFetchException extends RuntimeException {

    public PlatformLogisticsFetchException(String message) {
        super(message);
    }

    public PlatformLogisticsFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
