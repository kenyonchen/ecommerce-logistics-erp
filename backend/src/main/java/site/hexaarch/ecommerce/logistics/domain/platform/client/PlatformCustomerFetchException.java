package site.hexaarch.ecommerce.logistics.domain.platform.client;

import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;

/**
 * 第三方平台客户（买家）数据获取失败时抛出
 *
 * @author kenyon
 */
public class PlatformCustomerFetchException extends BusinessException {

    public PlatformCustomerFetchException(String message) {
        super(message, "PLATFORM_FETCH_ERROR");
    }

    public PlatformCustomerFetchException(String message, Throwable cause) {
        super(message, "PLATFORM_FETCH_ERROR", cause);
    }
}
