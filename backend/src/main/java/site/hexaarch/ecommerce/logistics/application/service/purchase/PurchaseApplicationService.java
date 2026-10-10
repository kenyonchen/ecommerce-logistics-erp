package site.hexaarch.ecommerce.logistics.application.service.purchase;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.application.service.finance.FinanceApplicationService;
import site.hexaarch.ecommerce.logistics.application.service.support.TenantAccessGuard;
import site.hexaarch.ecommerce.logistics.application.service.warehouse.WarehouseApplicationService;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionType;
import site.hexaarch.ecommerce.logistics.domain.purchase.aggregate.PurchaseOrder;
import site.hexaarch.ecommerce.logistics.domain.purchase.entity.PurchaseOrderItem;
import site.hexaarch.ecommerce.logistics.domain.purchase.repository.PurchaseQuery;
import site.hexaarch.ecommerce.logistics.domain.purchase.service.PurchaseOrderService;
import site.hexaarch.ecommerce.logistics.domain.purchase.valueobject.PurchaseStatus;
import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.Warehouse;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * 采购应用服务，负责协调领域对象完成采购相关的业务操作。
 * <p>
 * 到货（DELIVERED）是同事务内的同步编排：先加库存再生成采购应付，任一失败整体回滚。
 * 领域事件照常发布，但只作为对外订阅通道，仓内不重复消费，避免与编排双写。
 * </p>
 *
 * @author kenyon
 */
@Slf4j
@Service
public class PurchaseApplicationService {

    public static final String DEFAULT_TENANT_ID = "default-tenant";

    /**
     * 财务流水对采购单的引用类型（多态外键 reference_type，见 docs/db/er-diagram.md）
     */
    public static final String PAYABLE_REFERENCE_TYPE = "PURCHASE_ORDER";

    private static final String DEFAULT_CURRENCY = "CNY";

    private final PurchaseOrderService purchaseOrderService;
    private final WarehouseApplicationService warehouseApplicationService;
    private final FinanceApplicationService financeApplicationService;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 构造函数，注入采购单领域服务与协作上下文。
     *
     * @param purchaseOrderService         采购单领域服务
     * @param warehouseApplicationService  仓储应用服务（到货入库）
     * @param financeApplicationService    财务应用服务（采购应付）
     * @param domainEventPublisher         领域事件发布服务
     */
    public PurchaseApplicationService(PurchaseOrderService purchaseOrderService,
                                      WarehouseApplicationService warehouseApplicationService,
                                      FinanceApplicationService financeApplicationService,
                                      DomainEventPublisher domainEventPublisher) {
        this.purchaseOrderService = purchaseOrderService;
        this.warehouseApplicationService = warehouseApplicationService;
        this.financeApplicationService = financeApplicationService;
        this.domainEventPublisher = domainEventPublisher;
    }

    /**
     * 创建采购单并一次性写入明细行。
     * <p>
     * 明细行是必填参数而不是可选项：BR-09 的到货入库逐行 {@code inventoryIn}，只有单头的空单
     * 审批通过后入不了任何库存（ISS-143 实测 366 张自动单全部 0 行明细）。
     * 确实要建"先建单、后补明细"的单，走 {@link #addPurchaseOrderItem} —— 空的
     * {@code items} 只在该显式意图下才成立，所以这里不再提供省略参数的重载。
     * </p>
     *
     * @param tenantId     租户ID
     * @param supplierId   供应商ID
     * @param supplierName 供应商名称
     * @param warehouseId  仓库ID
     * @param items        明细行
     * @return 创建的采购单（含数据库生成的采购项ID）
     */
    @Transactional
    public PurchaseOrder createPurchaseOrder(String tenantId, String supplierId, String supplierName, String warehouseId,
                                             List<PurchaseOrderItem> items) {
        requireWarehouseOfTenant(tenantId, warehouseId);
        PurchaseOrder purchaseOrder = purchaseOrderService.createPurchaseOrder(tenantId, supplierId, supplierName,
                warehouseId, items);
        // 发布采购单聚合中的所有领域事件
        domainEventPublisher.publishEventsFrom(purchaseOrder);
        // 明细主键由数据库自增生成，回读一次让出参带上可用的采购项ID（否则刚建完的单改删无键可用）
        return purchaseOrderService.findPurchaseOrderById(tenantId, purchaseOrder.getPurchaseOrderId());
    }

    /**
     * 在指定租户内按采购单号查找采购单。
     *
     * @param tenantId        租户ID
     * @param purchaseOrderId 采购单号
     * @return 采购单实例
     */
    @Transactional(readOnly = true)
    public PurchaseOrder findPurchaseOrderById(String tenantId, String purchaseOrderId) {
        return purchaseOrderService.findPurchaseOrderById(tenantId, purchaseOrderId);
    }

    /**
     * 分页 + 条件查询采购单。
     *
     * @param tenantId       租户ID
     * @param purchaseOrderId 采购单号（模糊）
     * @param supplierId     供应商ID
     * @param supplierName   供应商名称（模糊）
     * @param warehouseId    收货仓库ID
     * @param status         采购状态
     * @param minTotalAmount 总金额下限
     * @param startDate      创建日期起（yyyy-MM-dd）
     * @param endDate        创建日期止（yyyy-MM-dd）
     * @param page           页码，从0开始
     * @param size           每页条数
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<PurchaseOrder> searchPurchaseOrders(String tenantId, String purchaseOrderId, String supplierId,
                                                          String supplierName, String warehouseId, PurchaseStatus status,
                                                          BigDecimal minTotalAmount, String startDate, String endDate,
                                                          int page, int size) {
        // 列表按登录租户收敛：仓储层 tenantId 为空即不加租户条件，会返回全平台数据（ISS-050）
        PurchaseQuery query = new PurchaseQuery(TenantAccessGuard.scopedTenantId(tenantId), purchaseOrderId, supplierId,
                supplierName, warehouseId,
                status, minTotalAmount, parseStartDate(startDate), parseEndDate(endDate));
        return purchaseOrderService.searchPurchaseOrders(query, page, size);
    }

    /**
     * 添加采购项到采购单。
     *
     * @param tenantId        租户ID
     * @param purchaseOrderId 采购单号
     * @param skuCode         SKU编码
     * @param productName     产品名称
     * @param quantity        数量
     * @param unitPrice       单价
     * @return 更新后的采购单（含数据库生成的采购项ID）
     */
    @Transactional
    public PurchaseOrder addPurchaseOrderItem(String tenantId, String purchaseOrderId, String skuCode,
                                              String productName, Integer quantity, BigDecimal unitPrice) {
        var purchaseOrder = purchaseOrderService.addPurchaseOrderItem(tenantId, purchaseOrderId, skuCode,
                productName, quantity, unitPrice);
        domainEventPublisher.publishEventsFrom(purchaseOrder);
        // 明细主键由数据库自增生成，回读一次让出参带上可用的采购项ID（否则前端改删无键可用）
        return purchaseOrderService.findPurchaseOrderById(tenantId, purchaseOrderId);
    }

    /**
     * 修改采购项的数量与单价。
     *
     * @param tenantId        租户ID
     * @param purchaseOrderId 采购单号
     * @param itemId          采购项ID
     * @param quantity        新数量
     * @param unitPrice       新单价
     * @return 更新后的采购单
     */
    @Transactional
    public PurchaseOrder updatePurchaseOrderItem(String tenantId, String purchaseOrderId, String itemId,
                                                 Integer quantity, BigDecimal unitPrice) {
        var purchaseOrder = purchaseOrderService.updatePurchaseOrderItem(tenantId, purchaseOrderId, itemId,
                quantity, unitPrice);
        domainEventPublisher.publishEventsFrom(purchaseOrder);
        return purchaseOrderService.findPurchaseOrderById(tenantId, purchaseOrderId);
    }

    /**
     * 删除采购项。
     *
     * @param tenantId        租户ID
     * @param purchaseOrderId 采购单号
     * @param itemId          采购项ID
     * @return 更新后的采购单
     */
    @Transactional
    public PurchaseOrder removePurchaseOrderItem(String tenantId, String purchaseOrderId, String itemId) {
        var purchaseOrder = purchaseOrderService.removePurchaseOrderItem(tenantId, purchaseOrderId, itemId);
        domainEventPublisher.publishEventsFrom(purchaseOrder);
        return purchaseOrderService.findPurchaseOrderById(tenantId, purchaseOrderId);
    }

    /**
     * 批准采购单。
     *
     * @param tenantId        租户ID
     * @param purchaseOrderId 采购单号
     * @param approvedBy      审批人
     * @param comment         审批意见
     * @return 批准后的采购单
     */
    @Transactional
    public PurchaseOrder approvePurchaseOrder(String tenantId, String purchaseOrderId, String approvedBy, String comment) {
        PurchaseOrder purchaseOrder = purchaseOrderService.approvePurchaseOrder(tenantId, purchaseOrderId, approvedBy, comment);
        // 发布采购单聚合中的所有领域事件
        domainEventPublisher.publishEventsFrom(purchaseOrder);
        return purchaseOrder;
    }

    /**
     * 拒绝采购单。
     *
     * @param tenantId        租户ID
     * @param purchaseOrderId 采购单号
     * @param approvedBy      审批人
     * @param reason          拒绝原因
     * @return 拒绝后的采购单
     */
    @Transactional
    public PurchaseOrder rejectPurchaseOrder(String tenantId, String purchaseOrderId, String approvedBy, String reason) {
        PurchaseOrder purchaseOrder = purchaseOrderService.rejectPurchaseOrder(tenantId, purchaseOrderId, approvedBy, reason);
        domainEventPublisher.publishEventsFrom(purchaseOrder);
        return purchaseOrder;
    }

    /**
     * 标记采购单为运输中。
     *
     * @param tenantId        租户ID
     * @param purchaseOrderId 采购单号
     * @return 更新后的采购单
     */
    @Transactional
    public PurchaseOrder markPurchaseOrderAsInTransit(String tenantId, String purchaseOrderId) {
        PurchaseOrder purchaseOrder = purchaseOrderService.markPurchaseOrderAsInTransit(tenantId, purchaseOrderId);
        domainEventPublisher.publishEventsFrom(purchaseOrder);
        return purchaseOrder;
    }

    /**
     * 标记采购单为已送达，并同步完成到货入库与采购应付入账。
     *
     * @param tenantId        租户ID
     * @param purchaseOrderId 采购单号
     * @return 更新后的采购单
     */
    @Transactional
    public PurchaseOrder markPurchaseOrderAsDelivered(String tenantId, String purchaseOrderId) {
        PurchaseOrder purchaseOrder = purchaseOrderService.markPurchaseOrderAsDelivered(tenantId, purchaseOrderId);
        domainEventPublisher.publishEventsFrom(purchaseOrder);
        receiveIntoWarehouse(purchaseOrder);
        createPurchasePayable(purchaseOrder);
        return purchaseOrder;
    }

    /**
     * 取消采购单。
     *
     * @param tenantId        租户ID
     * @param purchaseOrderId 采购单号
     * @return 取消后的采购单
     */
    @Transactional
    public PurchaseOrder cancelPurchaseOrder(String tenantId, String purchaseOrderId) {
        PurchaseOrder purchaseOrder = purchaseOrderService.cancelPurchaseOrder(tenantId, purchaseOrderId);
        domainEventPublisher.publishEventsFrom(purchaseOrder);
        return purchaseOrder;
    }

    /**
     * 到货入库：按明细行逐条增加收货仓库库存（BR-08 / BR-09）。
     */
    private void receiveIntoWarehouse(PurchaseOrder purchaseOrder) {
        List<PurchaseOrderItem> items = purchaseOrder.getItems();
        if (items == null || items.isEmpty()) {
            log.warn("[Purchase] 采购单 {} 无明细行，到货跳过入库", purchaseOrder.getPurchaseOrderId());
            return;
        }
        for (PurchaseOrderItem item : items) {
            warehouseApplicationService.inventoryIn(item.getSkuCode(), purchaseOrder.getWarehouseId(),
                    item.getQuantity());
        }
        log.info("[Purchase] 采购单 {} 到货入库完成，仓库={} 明细行数={}",
                purchaseOrder.getPurchaseOrderId(), purchaseOrder.getWarehouseId(), items.size());
    }

    /**
     * 采购应付入账（BR-10）：TransactionType 没有「应付」语义，按资金流出记 EXPENSE，
     * 靠 referenceType=PURCHASE_ORDER 与订单号还原为采购应付。
     */
    private void createPurchasePayable(PurchaseOrder purchaseOrder) {
        BigDecimal totalAmount = purchaseOrder.getTotalAmount();
        if (totalAmount == null || totalAmount.signum() <= 0) {
            log.warn("[Purchase] 采购单 {} 金额为 0，到货不生成应付流水", purchaseOrder.getPurchaseOrderId());
            return;
        }
        String description = String.format("采购单 %s 到货入库应付，供应商：%s",
                purchaseOrder.getPurchaseOrderId(), purchaseOrder.getSupplierName());
        financeApplicationService.createFinancialTransaction(TransactionType.EXPENSE, totalAmount, DEFAULT_CURRENCY,
                purchaseOrder.getPurchaseOrderId(), PAYABLE_REFERENCE_TYPE, description, purchaseOrder.getTenantId());
        log.info("[Purchase] 采购单 {} 已生成采购应付 {} {}", purchaseOrder.getPurchaseOrderId(), DEFAULT_CURRENCY, totalAmount);
    }

    /**
     * 收货仓库必须存在且属于同一租户（BR-01）。
     */
    private void requireWarehouseOfTenant(String tenantId, String warehouseId) {
        Warehouse warehouse;
        try {
            warehouse = warehouseApplicationService.findWarehouseById(warehouseId);
        } catch (EntityNotFoundException e) {
            throw new ValidationException("收货仓库不存在: " + warehouseId);
        }
        String warehouseTenantId = warehouse.getTenantId();
        // 历史仓库可能没有租户归属，只在双方都有值时判矛盾
        if (warehouseTenantId != null && !warehouseTenantId.isBlank()
                && tenantId != null && !tenantId.isBlank() && !warehouseTenantId.equals(tenantId)) {
            throw new ValidationException("收货仓库不属于当前租户: " + warehouseId);
        }
    }

    private LocalDateTime parseStartDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim()).atStartOfDay();
        } catch (DateTimeParseException e) {
            throw new ValidationException("开始日期格式不正确（应为 yyyy-MM-dd）: " + text);
        }
    }

    private LocalDateTime parseEndDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.of(LocalDate.parse(text.trim()), LocalTime.MAX);
        } catch (DateTimeParseException e) {
            throw new ValidationException("结束日期格式不正确（应为 yyyy-MM-dd）: " + text);
        }
    }
}
