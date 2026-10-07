package site.hexaarch.ecommerce.logistics.domain.finance.valueobject;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 标准化后的平台财务流水行（导入中间模型）。
 *
 * @param rowNumber             在原始数据中的行号（从1开始）
 * @param externalTransactionId 平台交易流水号（幂等去重键）
 * @param type                  交易类型
 * @param amount                金额
 * @param currency              币种（ISO三字母）
 * @param referenceId           关联ID
 * @param referenceType         关联类型
 * @param description           描述
 * @param status                交易状态（缺省 SUCCESS）
 * @param occurredAt            发生时间
 * @author kenyon
 */
public record ImportedFinanceRow(int rowNumber,
                                 String externalTransactionId,
                                 TransactionType type,
                                 BigDecimal amount,
                                 String currency,
                                 String referenceId,
                                 String referenceType,
                                 String description,
                                 TransactionStatus status,
                                 LocalDateTime occurredAt) {
}
