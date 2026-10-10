package site.hexaarch.ecommerce.logistics.application.service.finance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.finance.aggregate.FinancialTransaction;
import site.hexaarch.ecommerce.logistics.domain.finance.service.FinancialTransactionService;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionStatus;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionType;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;
import site.hexaarch.ecommerce.logistics.infrastructure.security.TenantContext;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 单据删除时的运费流水撤销口径（ISS-139 / R-59）。
 * <p>
 * 实测缺陷：{@code DELETE /api/logistics/{id}} 返回 200，物流单与 {@code logistics_fees} 行都没了
 * （{@code orphanRemoval} 生效），台账里那条 {@code expense/pending} 却原样留着 ——
 * 关联单据号指向一张不存在的物流单，等于账上挂了一笔付不出去也查不到来源的应付。
 * </p>
 * <p>
 * 口径：挂账中（PENDING）的置为 CANCELLED <b>留痕</b>而不是删行；已确认（SUCCESS）的分录
 * <b>拒绝</b>随单据消失并回业务原因（{@code FinancialTransaction.cancel()} 本就禁止从 SUCCESS 出发，
 * 这里提前成可读的拒绝，而不是让删除接口抛 500）。
 * </p>
 *
 * @author kenyon
 */
@ExtendWith(MockitoExtension.class)
class FreightExpenseReversalTest {

    private static final String LOGISTICS_ORDER_ID = "lg-1";
    private static final String REFERENCE_TYPE = FinanceApplicationService.LOGISTICS_ORDER_REFERENCE_TYPE;
    private static final String LABEL = "物流单 LG-1";

    @Mock
    private FinancialTransactionService financialTransactionService;
    @Mock
    private DomainEventPublisher domainEventPublisher;
    @InjectMocks
    private FinanceApplicationService financeApplicationService;

    @BeforeEach
    void setUp() {
        TenantContext.clearTenantId();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clearTenantId();
    }

    private FinancialTransaction transaction(String id, TransactionStatus status) {
        return FinancialTransaction.builder()
                .transactionId(id)
                .tenantId("tenant-a")
                .type(TransactionType.EXPENSE)
                .amount(new BigDecimal("15.00"))
                .currency("CNY")
                .referenceId(LOGISTICS_ORDER_ID)
                .referenceType(REFERENCE_TYPE)
                .description(LABEL + " 运费入账")
                .status(status)
                .build();
    }

    @Test
    @DisplayName("挂账中的运费流水随单据删除置为已取消，且不会被删行")
    void pendingFreightExpenseIsCancelledNotDeleted() {
        FinancialTransaction pending = transaction("TXN-P", TransactionStatus.PENDING);
        when(financialTransactionService.findFinancialTransactionsByReference(LOGISTICS_ORDER_ID, REFERENCE_TYPE))
                .thenReturn(List.of(pending));
        when(financialTransactionService.cancelFinancialTransaction("TXN-P")).thenReturn(pending);

        financeApplicationService.reverseTransactionsForDocument(LOGISTICS_ORDER_ID, REFERENCE_TYPE, LABEL);

        verify(financialTransactionService).cancelFinancialTransaction("TXN-P");
        // 分录只能撤销不能销毁：FinancialTransactionService 根本没有删流水的方法，
        // 这条形状由 FreightPostingGuardTest 的「财务侧不得出现 deleteFinancialTransaction」钉住
    }

    @Test
    @DisplayName("已确认成功的分录拒绝随单据消失，回可读业务原因")
    void confirmedEntryBlocksDocumentDeletion() {
        when(financialTransactionService.findFinancialTransactionsByReference(LOGISTICS_ORDER_ID, REFERENCE_TYPE))
                .thenReturn(List.of(transaction("TXN-S", TransactionStatus.SUCCESS)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> financeApplicationService.reverseTransactionsForDocument(LOGISTICS_ORDER_ID, REFERENCE_TYPE, LABEL));

        assertTrue(exception.getMessage().contains(LABEL),
                "拒绝原因要点名是哪张单据：" + exception.getMessage());
        assertTrue(exception.getMessage().contains("不能连同单据一起删除"),
                "拒绝原因要说明为什么不能删：" + exception.getMessage());
        verify(financialTransactionService, never()).cancelFinancialTransaction(any());
    }

    @Test
    @DisplayName("已取消/已失败的历史分录不再动，避免重复撤销")
    void closedEntriesAreLeftAlone() {
        when(financialTransactionService.findFinancialTransactionsByReference(LOGISTICS_ORDER_ID, REFERENCE_TYPE))
                .thenReturn(List.of(
                        transaction("TXN-C", TransactionStatus.CANCELLED),
                        transaction("TXN-F", TransactionStatus.FAILED)));

        financeApplicationService.reverseTransactionsForDocument(LOGISTICS_ORDER_ID, REFERENCE_TYPE, LABEL);

        verify(financialTransactionService, never()).cancelFinancialTransaction(any());
    }

    @Test
    @DisplayName("没有流水的单据照常删除（不入账的历史单据不该被撤销逻辑挡住）")
    void documentWithoutTransactionsReversesNothing() {
        when(financialTransactionService.findFinancialTransactionsByReference(LOGISTICS_ORDER_ID, REFERENCE_TYPE))
                .thenReturn(List.of());

        financeApplicationService.reverseTransactionsForDocument(LOGISTICS_ORDER_ID, REFERENCE_TYPE, LABEL);

        verify(financialTransactionService, never()).cancelFinancialTransaction(any());
    }
}
