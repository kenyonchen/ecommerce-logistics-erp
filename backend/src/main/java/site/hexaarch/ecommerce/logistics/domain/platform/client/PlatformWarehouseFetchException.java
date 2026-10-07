package site.hexaarch.ecommerce.logistics.domain.platform.client;

/**
 * 拉取平台店铺仓库数据失败异常。
 *
 * @author kenyon
 */
public class PlatformWarehouseFetchException extends RuntimeException {

    public PlatformWarehouseFetchException(String message) {
        super(message);
    }

    public PlatformWarehouseFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
