package site.hexaarch.ecommerce.logistics.infrastructure.platform;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.domain.platform.client.FinanceFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformFinanceFetcher;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台财务数据示例适配器。
 * <p>
 * 用途：在尚未完成真实平台联调、或需要演示/自测财务导入链路时使用；
 * 同时它也是HTTP适配器的降级实现，保证「拉取 -> 字段映射 -> 批量导入 -> 结果反馈」链路始终可跑通。
 * </p>
 *
 * @author kenyon
 */
@Slf4j
@Component
public class SamplePlatformFinanceFetcher implements PlatformFinanceFetcher {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /**
     * 各平台常见的结算流水类型词，用于验证类型转换规则
     */
    private static final String[] AMAZON_TYPES = {"payout", "refund", "fee"};
    private static final String[] EBAY_TYPES = {"sale", "refund", "commission"};
    private static final String[] SHOPIFY_TYPES = {"payout", "refund", "adjustment"};
    private static final String[] DEFAULT_TYPES = {"income", "expense", "refund"};

    @Override
    public String type() {
        return "SAMPLE";
    }

    @Override
    public List<Map<String, Object>> fetch(FinanceFetchRequest request) {
        int limit = request.limit() == null || request.limit() <= 0 ? 5 : Math.min(request.limit(), 50);
        String platform = request.platform() == null ? "OTHER" : request.platform().toUpperCase();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i <= limit; i++) {
            rows.add(buildRow(platform, i));
        }
        log.info("[PlatformFinanceFetch] 使用示例数据 platform={} rows={}", platform, rows.size());
        return rows;
    }

    private Map<String, Object> buildRow(String platform, int index) {
        String suffix = String.format("%03d", index);
        String externalTransactionId = platform + "-FIN-" + suffix;

        Map<String, Object> row = new LinkedHashMap<>();
        switch (platform) {
            case "AMAZON" -> {
                row.put("transaction-id", externalTransactionId);
                row.put("transaction-type", AMAZON_TYPES[index % AMAZON_TYPES.length]);
                row.put("amount", 199.0 + index * 10);
                row.put("currency", "CNY");
                row.put("order-id", "AMAZON-ORD-" + suffix);
                row.put("description", "亚马逊结算流水 " + suffix);
                row.put("status", "Success");
                row.put("date", LocalDateTime.now().minusDays(index).format(FORMATTER));
                row.put("platform", platform);
                row.put("payment-method", "BANK_DEPOSIT");
                row.put("fee-amount", 2.35 + index);
            }
            case "SHOPIFY", "MAGENTO" -> {
                row.put("payoutId", externalTransactionId);
                row.put("type", SHOPIFY_TYPES[index % SHOPIFY_TYPES.length]);
                row.put("grossAmount", 289.0 + index * 10);
                row.put("currencyCode", "CNY");
                row.put("referenceId", "SHOPIFY-ORD-" + suffix);
                row.put("referenceType", "ORDER");
                row.put("description", "Shopify 回款 " + suffix);
                row.put("status", "paid");
                row.put("settlementDate", LocalDateTime.now().minusDays(index).format(FORMATTER));
                row.put("platform", platform);
                row.put("paymentMethod", "SHOPIFY_PAY");
                row.put("feeAmount", 3.15 + index);
            }
            case "EBAY", "ALIEXPRESS", "WISH" -> {
                row.put("externalTransactionId", externalTransactionId);
                row.put("movementType", EBAY_TYPES[index % EBAY_TYPES.length]);
                row.put("value", 159.0 + index * 10);
                row.put("currency", "CNY");
                row.put("referenceId", "EBAY-ORD-" + suffix);
                row.put("referenceType", "ORDER");
                row.put("memo", "eBay 账务流水 " + suffix);
                row.put("state", "completed");
                row.put("transactionDate", LocalDateTime.now().minusDays(index).format(FORMATTER));
                row.put("platform", platform);
                row.put("paymentMethod", "PAYPAL");
                row.put("feeAmount", 1.99 + index);
            }
            default -> {
                row.put("externalTransactionId", externalTransactionId);
                row.put("type", DEFAULT_TYPES[index % DEFAULT_TYPES.length]);
                row.put("amount", 99.0 + index * 10);
                row.put("currency", "CNY");
                row.put("referenceId", "ORD-" + suffix);
                row.put("referenceType", "ORDER");
                row.put("description", "通用示例财务流水 " + suffix);
                row.put("status", "SUCCESS");
                row.put("occurredAt", LocalDateTime.now().minusDays(index).format(FORMATTER));
                row.put("platform", platform);
                row.put("paymentMethod", "BANK_TRANSFER");
                row.put("feeAmount", 0.99 + index);
            }
        }
        // 故意保留一行异常数据（缺少类型与币种），用于验证导入过程的行级异常处理
        if (index == 2) {
            row.remove("transaction-type");
            row.remove("type");
            row.remove("movementType");
            row.remove("currency");
            row.remove("currencyCode");
        }
        return row;
    }
}
