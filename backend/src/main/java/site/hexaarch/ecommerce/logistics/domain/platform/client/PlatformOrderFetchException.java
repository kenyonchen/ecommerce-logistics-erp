package site.hexaarch.ecommerce.logistics.domain.platform.client;

import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;

/**
 * 第三方平台订单数据获取失败时抛出
 *
 * @author kenyon
 */
public class PlatformOrderFetchException extends BusinessException {

    public PlatformOrderFetchException(String message) {
        super(message, "PLATFORM_FETCH_ERROR");
    }

    public PlatformOrderFetchException(String message, Throwable cause) {
        super(message, "PLATFORM_FETCH_ERROR", cause);
    }
}
