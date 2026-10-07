package site.hexaarch.ecommerce.logistics.domain.platform.client;

import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;

/**
 * 第三方平台财务数据获取失败时抛出
 *
 * @author kenyon
 */
public class FinanceFetchException extends BusinessException {

    public FinanceFetchException(String message) {
        super(message, "PLATFORM_FETCH_ERROR");
    }

    public FinanceFetchException(String message, Throwable cause) {
        super(message, "PLATFORM_FETCH_ERROR", cause);
    }
}
