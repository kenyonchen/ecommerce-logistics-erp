package site.hexaarch.ecommerce.logistics.domain.logistics.valueobject;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 物流导入的标准目标字段及其常见别名。
 * 各电商/物流平台返回的物流字段命名差异较大，通过该枚举建立「平台原始字段 -> 标准物流字段」的映射基础。
 *
 * @author kenyon
 */
public enum LogisticsImportTargetField {
    EXTERNAL_LOGISTICS_ID("平台物流单号", true, "externalLogisticsId", "external_logistics_id", "logisticsId",
            "logistics_id", "shipmentId", "shipment_id", "fulfillmentId", "fulfillment_id", "平台物流单号", "物流单号ID"),
    TRACKING_NUMBER("运单号", false, "trackingNumber", "tracking_number", "trackingNo", "tracking_no", "waybillNo",
            "expressNo", "express_no", "mailNo", "运单号", "快递单号"),
    ORDER_NUMBER("系统订单号", false, "orderNumber", "order_number", "orderNo", "订单号"),
    EXTERNAL_ORDER_ID("平台订单号", false, "externalOrderId", "external_order_id", "platformOrderId",
            "platform_order_id", "orderId", "order_id", "amazonOrderId", "amazon_order_id", "saleOrder",
            "平台订单号", "店铺订单号"),
    CHANNEL_CODE("物流渠道编码", false, "channelCode", "channel_code", "logisticsChannel", "logistics_channel",
            "channel", "expressCode", "物流渠道", "渠道编码"),
    CARRIER("承运商", false, "carrier", "company", "expressCompany", "express_company", "logisticsCompany",
            "logistics_company", "supplierName", "承运商", "物流公司", "快递公司"),
    STATUS("物流状态", false, "status", "logisticsStatus", "logistics_status", "shippingStatus", "traceStatus",
            "物流状态", "状态"),
    TRACKING_URL("物流跟踪链接", false, "trackingUrl", "tracking_url", "trackUrl", "shippingUrl", "shipping_url",
            "queryUrl", "跟踪链接"),
    LABEL_URL("面单下载地址", false, "labelUrl", "label_url", "printUrl", "面单地址", "面单链接"),
    LABEL_FORMAT("面单格式", false, "labelFormat", "label_format", "面单格式"),
    BARCODE("条形码", false, "barcode", "barCode", "条形码"),
    SHIPPED_AT("发货时间", false, "shippedAt", "shipped_at", "shipDate", "ship_date", "consignTime", "consign_time",
            "sendTime", "deliverTime", "发货时间", "出库时间"),
    ESTIMATED_ARRIVAL_AT("预计送达时间", false, "estimatedArrivalAt", "estimated_arrival_at",
            "estimatedDeliveryDate", "estimated_delivery_date", "promiseTime", "预计送达时间"),
    DELIVERED_AT("签收时间", false, "deliveredAt", "delivered_at", "deliveredDate", "delivered_date", "signedTime",
            "signTime", "签收时间"),
    TOTAL_FEE("物流费用", false, "totalFee", "total_fee", "freight", "shippingFee", "shipping_fee", "logisticsFee",
            "logistics_fee", "cost", "物流费用", "运费"),
    CURRENCY("币种", false, "currency", "currencyCode", "币种"),
    RECIPIENT("收件人", false, "recipient", "recipientName", "receiver", "receiverName", "consignee", "收件人"),
    PHONE("收件人电话", false, "phone", "phoneNumber", "recipientPhone", "receiverPhone", "mobile", "tel", "收件人电话",
            "联系电话"),
    COUNTRY("国家", false, "country", "countryCode", "国家"),
    PROVINCE("省份", false, "province", "state", "stateOrProvince", "provinceName", "省", "省份"),
    CITY("城市", false, "city", "cityName", "市", "城市"),
    STREET("详细地址", false, "street", "address", "detailAddress", "streetAddress", "addressLine1", "详细地址", "地址"),
    ZIP_CODE("邮编", false, "zipCode", "zip_code", "postalCode", "zipcode", "邮编"),
    TRACES("物流轨迹", false, "traces", "traceList", "trace_list", "trackList", "logisticsTraces", "eventHistory",
            "event_history", "轨迹", "物流轨迹"),
    TRACE_TIME("单条轨迹时间", false, "traceTime", "trace_time", "acceptTime", "轨迹时间"),
    TRACE_STATUS("单条轨迹状态", false, "traceStatusName", "物流节点"),
    TRACE_DESCRIPTION("单条轨迹描述", false, "traceDescription", "trace_description", "description", "remark",
            "物流描述", "轨迹描述"),
    TRACE_LOCATION("单条轨迹地点", false, "traceLocation", "trace_location", "location", "city_code", "轨迹地点");

    private final String label;
    private final boolean required;
    private final List<String> aliases;

    LogisticsImportTargetField(String label, boolean required, String... aliases) {
        this.label = label;
        this.required = required;
        this.aliases = List.of(aliases);
    }

    public String getLabel() {
        return label;
    }

    public boolean isRequired() {
        return required;
    }

    public List<String> getAliases() {
        return aliases;
    }

    /**
     * 根据平台原始字段名自动推断目标字段（忽略大小写、连字符与下划线）
     */
    public static Optional<LogisticsImportTargetField> detectByAlias(String sourceField) {
        if (sourceField == null || sourceField.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalize(sourceField);
        for (LogisticsImportTargetField field : values()) {
            for (String alias : field.aliases) {
                if (normalized.equals(normalize(alias))) {
                    return Optional.of(field);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 候选别名匹配：允许包含关系
     */
    public static Optional<LogisticsImportTargetField> detectFuzzy(String sourceField) {
        if (sourceField == null || sourceField.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalize(sourceField);
        for (LogisticsImportTargetField field : values()) {
            for (String alias : field.aliases) {
                String normalizedAlias = normalize(alias);
                if (normalizedAlias.length() >= 4
                        && (normalized.contains(normalizedAlias) || normalizedAlias.contains(normalized))) {
                    return Optional.of(field);
                }
            }
        }
        return Optional.empty();
    }

    public static List<LogisticsImportTargetField> requiredFields() {
        return Arrays.stream(values()).filter(LogisticsImportTargetField::isRequired).toList();
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("[\\s\\-_]", "");
    }
}
