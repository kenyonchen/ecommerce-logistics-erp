package site.hexaarch.ecommerce.logistics.domain.platform.client;

import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;

/**
 * 第三方平台商品数据获取失败时抛出
 *
 * @author kenyon
 */
public class PlatformProductFetchException extends BusinessException {

    public PlatformProductFetchException(String message) {
        super(message, "PLATFORM_FETCH_ERROR");
    }

    public PlatformProductFetchException(String message, Throwable cause) {
        super(message, "PLATFORM_FETCH_ERROR", cause);
    }
}
