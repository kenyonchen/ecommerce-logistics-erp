package site.hexaarch.ecommerce.logistics.domain.finance.aggregate;

import org.junit.jupiter.api.Test;
import site.hexaarch.ecommerce.logistics.domain.finance.event.FinancialTransactionCreatedEvent;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionStatus;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionType;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 财务交易聚合不变式测试。
 *
 * @author kenyon
 */
class FinancialTransactionTest {

    private FinancialTransaction valid() {
        return FinancialTransaction.create("TXN-0001", "tenant-1", TransactionType.INCOME,
                new BigDecimal("1299.00"), "USD", "AMAZON:ORD-1", "PLATFORM_TRANSACTION", "平台回款");
    }

    @Test
    void shouldAcceptNormalPlatformAmounts() {
        FinancialTransaction transaction = valid();
        assertEquals(TransactionStatus.PENDING, transaction.getStatus());
        assertEquals(0, new BigDecimal("1299.00").compareTo(transaction.getAmount()));
        assertTrue(transaction.getDomainEvents().stream()
                .anyMatch(e -> e instanceof FinancialTransactionCreatedEvent));

        // 平台账单里的退款/手续费本身即为负数，方向由金额符号表达，必须允许
        FinancialTransaction refund = FinancialTransaction.create("TXN-0002", "tenant-1", TransactionType.REFUND,
                new BigDecimal("-18.5"), "USD", "AMAZON:ORD-2", "PLATFORM_TRANSACTION", "退款");
        assertEquals(0, new BigDecimal("-18.5").compareTo(refund.getAmount()));
    }

    @Test
    void shouldRejectMissingRequiredFields() {
        assertThrows(IllegalArgumentException.class, () -> FinancialTransaction.create(
                "TXN-0003", "tenant-1", null, new BigDecimal("10.00"), "USD", "REF-1", "ORDER", null));
        assertThrows(IllegalArgumentException.class, () -> FinancialTransaction.create(
                "TXN-0004", "tenant-1", TransactionType.INCOME, null, "USD", "REF-1", "ORDER", null));
        assertThrows(IllegalArgumentException.class, () -> FinancialTransaction.create(
                "TXN-0005", "tenant-1", TransactionType.INCOME, new BigDecimal("10.00"), " ", "REF-1", "ORDER", null));
        assertThrows(IllegalArgumentException.class, () -> FinancialTransaction.create(
                "TXN-0006", "tenant-1", TransactionType.INCOME, new BigDecimal("10.00"), "USD", null, "ORDER", null));
        assertThrows(IllegalArgumentException.class, () -> FinancialTransaction.create(
                "TXN-0007", "tenant-1", TransactionType.INCOME, new BigDecimal("10.00"), "USD", "REF-1", null, null));
    }

    @Test
    void shouldRejectZeroAmount() {
        IllegalArgumentException createEx = assertThrows(IllegalArgumentException.class,
                () -> FinancialTransaction.create("TXN-0008", "tenant-1", TransactionType.ADJUSTMENT,
                        BigDecimal.ZERO, "USD", "REF-1", "ORDER", null));
        assertTrue(createEx.getMessage().contains("不能为0"));

        FinancialTransaction transaction = valid();
        assertThrows(IllegalArgumentException.class,
                () -> transaction.applyPlatformUpdate(BigDecimal.ZERO, null, null));
        assertEquals(0, new BigDecimal("1299.00").compareTo(transaction.getAmount()),
                "被拒绝的更新不得改变已生效金额");
    }

    @Test
    void shouldGuardArgumentOrderBetweenTransactionIdAndTenantId() {
        assertThrows(IllegalArgumentException.class, () -> FinancialTransaction.create(
                "tenant-1", "tenant-1", TransactionType.INCOME, new BigDecimal("10.00"), "USD", "REF-1", "ORDER", null));
        assertThrows(IllegalArgumentException.class, () -> FinancialTransaction.create(
                null, "tenant-1", TransactionType.INCOME, new BigDecimal("10.00"), "USD", "REF-1", "ORDER", null));
    }

    @Test
    void statusTransitionsShouldOnlyComeFromPending() {
        FinancialTransaction completed = valid();
        completed.complete();
        assertEquals(TransactionStatus.SUCCESS, completed.getStatus());
        assertThrows(IllegalArgumentException.class, completed::complete);
        assertThrows(IllegalArgumentException.class, completed::cancel);

        FinancialTransaction failed = valid();
        failed.fail();
        assertEquals(TransactionStatus.FAILED, failed.getStatus());
        assertThrows(IllegalArgumentException.class, failed::complete);
    }
}
