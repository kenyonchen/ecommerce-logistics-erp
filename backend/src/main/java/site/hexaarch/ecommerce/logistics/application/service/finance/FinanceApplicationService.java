package site.hexaarch.ecommerce.logistics.application.service.finance;

import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.application.service.support.TenantAccessGuard;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.finance.aggregate.FinancialTransaction;
import site.hexaarch.ecommerce.logistics.domain.finance.service.FinancialTransactionService;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionStatus;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionType;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * 财务应用服务，负责协调领域对象完成财务相关的业务操作。
 *
 * @author kenyon
 */
@Service
public class FinanceApplicationService {

    public static final String DEFAULT_TENANT_ID = "default-tenant";

    /**
     * 运费流水的关联单据类型（BR-10 / ISS-139）。放在台账这一侧，是为了让入账
     * （{@code LogisticsApplicationService.postFreightExpense}）与撤销（物流单删除、
     * 订单级联删除）引用同一个字面量，而不是各写一份。
     */
    public static final String LOGISTICS_ORDER_REFERENCE_TYPE = "LOGISTICS_ORDER";

    private final FinancialTransactionService financialTransactionService;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 构造函数，注入财务交易领域服务。
     *
     * @param financialTransactionService 财务交易领域服务
     * @param domainEventPublisher        领域事件发布服务
     */
    public FinanceApplicationService(FinancialTransactionService financialTransactionService, DomainEventPublisher domainEventPublisher) {
        this.financialTransactionService = financialTransactionService;
        this.domainEventPublisher = domainEventPublisher;
    }

    /**
     * 创建财务交易（归属默认租户）。
     *
     * @param type          交易类型
     * @param amount        金额
     * @param currency      货币
     * @param referenceId   关联ID
     * @param referenceType 关联类型
     * @param description   描述
     * @return 创建的财务交易
     */
    @Transactional
    public FinancialTransaction createFinancialTransaction(
            TransactionType type,
            BigDecimal amount,
            String currency,
            String referenceId,
            String referenceType,
            String description) {
        return createFinancialTransaction(type, amount, currency, referenceId, referenceType, description, null);
    }

    /**
     * 创建财务交易。
     *
     * @param type          交易类型
     * @param amount        金额
     * @param currency      货币
     * @param referenceId   关联ID
     * @param referenceType 关联类型
     * @param description   描述
     * @param tenantId      租户ID（可空，空则归属默认租户）
     * @return 创建的财务交易
     */
    @Transactional
    public FinancialTransaction createFinancialTransaction(
            TransactionType type,
            BigDecimal amount,
            String currency,
            String referenceId,
            String referenceType,
            String description,
            String tenantId) {
        // 租户归属在应用层定死，不让领域服务用「null 就当 default-tenant」兜底：
        // 缺省 tenantId 的金额记录一旦落进没有租户行的 default-tenant 桶，财务列表永远查不到它（ISS-118）。
        FinancialTransaction transaction = financialTransactionService.createFinancialTransaction(
                type,
                amount,
                currency,
                referenceId,
                referenceType,
                description,
                TenantAccessGuard.resolveWriteTenant(tenantId, DEFAULT_TENANT_ID));
        // 发布财务交易聚合中的所有领域事件
        domainEventPublisher.publishEventsFrom(transaction);
        return transaction;
    }

    /**
     * 根据交易ID查找财务交易。
     *
     * @param transactionId 交易ID
     * @return 财务交易实例
     */
    public FinancialTransaction findFinancialTransactionById(String transactionId) {
        FinancialTransaction transaction = financialTransactionService.findFinancialTransactionById(transactionId);
        // 按ID直读财务流水不校验租户，先收敛归属（ISS-050）
        TenantAccessGuard.requireOwned("FinancialTransaction", transaction.getTenantId(), transactionId);
        return transaction;
    }

    /**
     * 根据状态查找财务交易。
     *
     * @param status 交易状态
     * @return 财务交易列表
     */
    public List<FinancialTransaction> findFinancialTransactionsByStatus(TransactionStatus status) {
        return withinCurrentTenant(financialTransactionService.findFinancialTransactionsByStatus(status));
    }

    /**
     * 根据类型查找财务交易。
     *
     * @param type 交易类型
     * @return 财务交易列表
     */
    public List<FinancialTransaction> findFinancialTransactionsByType(TransactionType type) {
        return withinCurrentTenant(financialTransactionService.findFinancialTransactionsByType(type));
    }

    /**
     * 根据关联ID和类型查找财务交易。
     *
     * @param referenceId   关联ID
     * @param referenceType 关联类型
     * @return 财务交易列表
     */
    public List<FinancialTransaction> findFinancialTransactionsByReference(String referenceId, String referenceType) {
        return withinCurrentTenant(financialTransactionService.findFinancialTransactionsByReference(referenceId, referenceType));
    }

    /**
     * 判断某张业务单据的某类流水是否已经入账（BR-03 的幂等判据，供业务联动调用）。
     * <p>
     * 这里<b>故意</b>不走 {@code withinCurrentTenant}：{@code referenceId} 就是调用方刚用
     * {@link TenantAccessGuard#requireOwned} 校验过归属的那张单据的 ID，按登录租户再过滤一遍会让
     * 平台管理员代客发货时看不到上一轮已入账的那条，于是同一笔运费记两次。
     * </p>
     *
     * @param referenceId   关联单据ID
     * @param referenceType 关联单据类型
     * @param type          流水类型
     * @return 已存在同类流水时为 true
     */
    public boolean existsFinancialTransaction(String referenceId, String referenceType, TransactionType type) {
        return financialTransactionService.findFinancialTransactionsByReference(referenceId, referenceType).stream()
                .anyMatch(transaction -> transaction.getType() == type);
    }

    /**
     * 按条件分页查询财务交易（按创建时间倒序）。
     *
     * @param tenantId      租户ID（可空）
     * @param type          交易类型（可空）
     * @param status        交易状态（可空）
     * @param referenceId   关联ID（可空）
     * @param referenceType 关联类型（可空）
     * @param from          创建时间起（可空）
     * @param to            创建时间止（可空）
     * @param page          页码，从0开始
     * @param size          每页条数
     * @return 分页结果
     */
    public PageResult<FinancialTransaction> searchFinancialTransactions(
            String tenantId,
            TransactionType type,
            TransactionStatus status,
            String referenceId,
            String referenceType,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int size) {
        // 列表按登录租户收敛：tenantId 为空会返回全平台流水（ISS-050）
        return financialTransactionService.searchFinancialTransactions(TenantAccessGuard.scopedTenantId(tenantId), type,
                status, referenceId, referenceType, from, to, page, size);
    }

    /**
     * 完成财务交易。
     *
     * @param transactionId 交易ID
     * @return 完成后的财务交易
     */
    @Transactional
    public FinancialTransaction completeFinancialTransaction(String transactionId) {
        FinancialTransaction transaction = financialTransactionService.completeFinancialTransaction(transactionId);
        // 领域服务按ID直改流水，返回后校验归属，跨租户改动随事务回滚（ISS-050）
        TenantAccessGuard.requireOwned("FinancialTransaction", transaction.getTenantId(), transactionId);
        // complete() 注册的 FinancialTransactionStatusChangedEvent 在此发布
        domainEventPublisher.publishEventsFrom(transaction);
        return transaction;
    }

    /**
     * 失败财务交易。
     *
     * @param transactionId 交易ID
     * @return 失败后的财务交易
     */
    @Transactional
    public FinancialTransaction failFinancialTransaction(String transactionId) {
        FinancialTransaction transaction = financialTransactionService.failFinancialTransaction(transactionId);
        // 同上：按登录租户校验归属，越权改动回滚（ISS-050）
        TenantAccessGuard.requireOwned("FinancialTransaction", transaction.getTenantId(), transactionId);
        // fail() 注册的 FinancialTransactionStatusChangedEvent 在此发布
        domainEventPublisher.publishEventsFrom(transaction);
        return transaction;
    }

    /**
     * 取消财务交易。
     *
     * @param transactionId 交易ID
     * @return 取消后的财务交易
     */
    @Transactional
    public FinancialTransaction cancelFinancialTransaction(String transactionId) {
        FinancialTransaction transaction = financialTransactionService.cancelFinancialTransaction(transactionId);
        // 同上：按登录租户校验归属，越权改动回滚（ISS-050）
        TenantAccessGuard.requireOwned("FinancialTransaction", transaction.getTenantId(), transactionId);
        // cancel() 注册的 FinancialTransactionStatusChangedEvent 在此发布
        domainEventPublisher.publishEventsFrom(transaction);
        return transaction;
    }

    /**
     * 列表按登录租户收敛；平台管理员或无租户上下文时不过滤（ISS-050）。
     */
    private List<FinancialTransaction> withinCurrentTenant(List<FinancialTransaction> transactions) {
        String tenantId = TenantAccessGuard.currentTenantId();
        if (tenantId == null) {
            return transactions;
        }
        return transactions.stream()
                .filter(transaction -> tenantId.equals(transaction.getTenantId()))
                .toList();
    }

    /**
     * 单据要从库里消失时，撤销由它派生的财务流水（ISS-139 / R-59）。
     * <p>
     * 修复前的形状：{@code DELETE /api/logistics/{id}} 与订单级联删除都只删单据行，
     * 台账里那条运费应付原样留着 —— 实测删完 {@code expense/pending} 行还在，
     * 关联单据号指向一张已经不存在的物流单。账上凭空多一笔付不出去的应付。
     * </p>
     * <p>
     * 口径：{@code PENDING}（挂账未确认）置为 {@code CANCELLED} 留痕，<b>不删行</b>——
     * 已过账的分录要能被审计追到；已 {@code SUCCESS} 的分录<b>拒绝</b>随单据消失，
     * 抛业务错误让调用方先去台账处理（{@code FinancialTransaction.cancel()} 本身就禁止
     * 从 SUCCESS 出发，这里只是把这个约束提前成可读的原因，而不是让删除接口 500）。
     * 与 {@link #existsFinancialTransaction} 同理不按登录租户二次过滤：{@code referenceId}
     * 已经是调用方校验过归属的那张单据。
     * </p>
     *
     * @param referenceId   关联单据ID
     * @param referenceType 关联单据类型
     * @param documentLabel 单据的可读标识，出现在拒绝原因里
     */
    @Transactional
    public void reverseTransactionsForDocument(String referenceId, String referenceType, String documentLabel) {
        List<FinancialTransaction> transactions =
                financialTransactionService.findFinancialTransactionsByReference(referenceId, referenceType);
        long confirmed = transactions.stream()
                .filter(transaction -> transaction.getStatus() == TransactionStatus.SUCCESS)
                .count();
        if (confirmed > 0) {
            throw new BusinessException(documentLabel + " 已有 " + confirmed
                    + " 笔确认成功的财务流水，不能连同单据一起删除；请先在财务台账处理这些流水");
        }
        transactions.stream()
                .filter(transaction -> transaction.getStatus() == TransactionStatus.PENDING)
                .forEach(transaction -> cancelFinancialTransaction(transaction.getTransactionId()));
    }
}
