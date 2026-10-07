package site.hexaarch.ecommerce.logistics.domain.platform.client;

import java.time.LocalDateTime;

/**
 * 第三方平台物流数据拉取请求参数。
 *
 * @param tenantId       租户ID
 * @param platform       平台名称
 * @param storeUrl       平台店铺地址
 * @param apiKey         平台API Key
 * @param apiSecret      平台API Secret
 * @param orderNumber    按订单号/平台订单号过滤
 * @param trackingNumber 按运单号过滤
 * @param shippedFrom    发货时间起
 * @param shippedTo      发货时间止
 * @param limit          拉取条数上限
 * @param page           页码（从1开始）
 * @author kenyon
 */
public record PlatformLogisticsFetchRequest(
        String tenantId,
        String platform,
        String storeUrl,
        String apiKey,
        String apiSecret,
        String orderNumber,
        String trackingNumber,
        LocalDateTime shippedFrom,
        LocalDateTime shippedTo,
        Integer limit,
        Integer page) {
}
