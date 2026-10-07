package site.hexaarch.ecommerce.logistics.domain.finance.valueobject;

import java.util.List;
import java.util.Optional;

/**
 * 财务导入的标准目标字段及其常见别名。
 * 第三方电商平台结算/回款流水的字段命名各不相同，通过该枚举建立「平台原始字段 -> 标准财务字段」的映射基础。
 *
 * @author kenyon
 */
public enum FinanceImportTargetField {
    EXTERNAL_TRANSACTION_ID("平台交易流水号", true,
            "externalTransactionId", "external_transaction_id", "transactionId", "transaction_id", "financeId",
            "finance_id", "paymentId", "payment_id", "payoutId", "payout_id", "settlementId", "流水号", "交易流水号",
            "平台交易号"),
    TYPE("交易类型", true,
            "type", "transactionType", "transaction_type", "movementType", "movement_type", "feeType", "fee_type",
            "类型", "交易类型"),
    AMOUNT("金额", true,
            "amount", "transactionAmount", "transaction_amount", "totalAmount", "total_amount", "grossAmount",
            "gross_amount", "settlementAmount", "settlement_amount", "value", "金额", "交易金额"),
    CURRENCY("币种", true, "currency", "currencyCode", "currency_code", "币种", "货币"),
    REFERENCE_ID("关联ID", false,
            "referenceId", "reference_id", "orderId", "order_id", "relatedId", "sourceId", "关联ID", "关联单号"),
    REFERENCE_TYPE("关联类型", false, "referenceType", "reference_type", "关联类型"),
    DESCRIPTION("描述", false, "description", "memo", "note", "remark", "描述", "备注"),
    STATUS("状态", false,
            "status", "state", "transactionStatus", "transaction_status", "settlementStatus", "状态"),
    OCCURRED_AT("发生时间", false,
            "occurredAt", "occurred_at", "date", "reportingDate", "transactionDate", "transaction_date",
            "settlementDate", "settlement_date", "完成时间", "发生时间");

    private final String label;
    private final boolean required;
    private final List<String> aliases;

    FinanceImportTargetField(String label, boolean required, String... aliases) {
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
    public static Optional<FinanceImportTargetField> detectByAlias(String sourceField) {
        if (sourceField == null || sourceField.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalize(sourceField);
        for (FinanceImportTargetField field : values()) {
            for (String alias : field.aliases) {
                if (normalized.equals(normalize(alias))) {
                    return Optional.of(field);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 候选别名匹配：允许包含关系，如 "transaction-amount-value" 命中 AMOUNT
     */
    public static Optional<FinanceImportTargetField> detectFuzzy(String sourceField) {
        if (sourceField == null || sourceField.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalize(sourceField);
        for (FinanceImportTargetField field : values()) {
            for (String alias : field.aliases) {
                String normalizedAlias = normalize(alias);
                if (normalizedAlias.length() >= 3
                        && (normalized.contains(normalizedAlias) || normalizedAlias.contains(normalized))) {
                    return Optional.of(field);
                }
            }
        }
        return Optional.empty();
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("[\\s\\-_]", "");
    }
}
