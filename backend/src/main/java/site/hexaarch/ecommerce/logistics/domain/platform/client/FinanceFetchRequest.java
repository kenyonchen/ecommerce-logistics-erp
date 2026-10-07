package site.hexaarch.ecommerce.logistics.domain.platform.client;

import java.time.LocalDateTime;

/**
 * 第三方平台财务数据拉取请求参数。
 *
 * @param tenantId              租户ID
 * @param platform              平台名称
 * @param storeUrl              店铺地址
 * @param apiKey                API Key
 * @param apiSecret             API Secret
 * @param externalTransactionId 指定平台交易流水号（可空）
 * @param updatedFrom           平台侧更新时间起（增量拉取，可空）
 * @param updatedTo             平台侧更新时间止（可空）
 * @param limit                 单页条数
 * @param page                  页码，从1开始
 * @author kenyon
 */
public record FinanceFetchRequest(
        String tenantId,
        String platform,
        String storeUrl,
        String apiKey,
        String apiSecret,
        String externalTransactionId,
        LocalDateTime updatedFrom,
        LocalDateTime updatedTo,
        Integer limit,
        Integer page
) {
}
