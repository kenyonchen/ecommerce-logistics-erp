package site.hexaarch.ecommerce.logistics.application.service.purchase;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.application.service.support.TenantAccessGuard;
import site.hexaarch.ecommerce.logistics.domain.product.aggregate.Product;
import site.hexaarch.ecommerce.logistics.domain.product.entity.SKU;
import site.hexaarch.ecommerce.logistics.domain.product.repository.ProductRepository;
import site.hexaarch.ecommerce.logistics.domain.purchase.aggregate.PurchaseOrder;
import site.hexaarch.ecommerce.logistics.domain.purchase.entity.PurchaseOrderItem;
import site.hexaarch.ecommerce.logistics.domain.warehouse.event.InventoryShortageEvent;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 缺货自动补货（BR-12）：库存不足事件 → 带明细行的采购单。
 * <p>
 * 进程内 {@code EventHandler} 与 MQ {@code InventoryShortageEventConsumer} 共用本方法，
 * 两条通道不能各写一套语义（ISS-054 的双写、ISS-143 的空单都是分叉的代价）。
 * </p>
 * <p>
 * 明细行不是可选项：BR-09 的到货入库是逐行 {@code inventoryIn}，只有单头的空单即使审批通过
 * 也入不了库，实测 366 张自动单全部 0 行明细（ISS-143）。
 * </p>
 *
 * @author kenyon
 */
@Slf4j
@Service
public class ReplenishmentApplicationService {

    private static final String SUPPLIER_ID = "DEFAULT_SUPPLIER";
    private static final String SUPPLIER_NAME = "Default Supplier";

    /**
     * 建议量下限：现存量已达阈值时算出 0，而 {@code PurchaseOrderItem.create} 要求数量 &gt; 0，
     * 直接建单会让整张单抛 {@code ValidationException} 被消费端的 catch 吞成一条 error 日志。
     */
    private static final int MIN_SUGGESTED_QUANTITY = 1;

    private final ProductRepository productRepository;
    private final PurchaseApplicationService purchaseApplicationService;

    public ReplenishmentApplicationService(ProductRepository productRepository,
                                           PurchaseApplicationService purchaseApplicationService) {
        this.productRepository = productRepository;
        this.purchaseApplicationService = purchaseApplicationService;
    }

    /**
     * 按缺货事件生成补货采购单：建议量 = 阈值 - 现存量（下限 1），单价 = SKU 成本价。
     * <p>
     * 定价取不到时<b>只记预警、不建单</b>：采购单价只能来自商品的成本价，拿售价或猜数顶替
     * 会造出一张能过审批却金额虚假的账，到货还会按假金额生成应付流水（与 R-58 同口径）。
     * </p>
     *
     * @param event 库存不足事件（携带仓库、租户、SKU、现存量与阈值）
     * @return 生成的采购单；未建单时为空
     */
    public Optional<PurchaseOrder> replenishForShortage(InventoryShortageEvent event) {
        String skuCode = event.getSku();
        if (skuCode == null || skuCode.isBlank()) {
            log.warn("[Replenishment] 缺货事件没有 SKU 编码（仓库={} 产品={}），无法定价与入库，跳过建单",
                    event.getWarehouseId(), event.getProductId());
            return Optional.empty();
        }
        // 归属只走唯一的写路径入口：载荷带租户就按载荷，会话里有登录租户就按登录租户，
        // 两者都没有才落历史默认桶（ISS-118 —— 绕过它就是把数据写进没有租户行的幻影桶）
        String tenantId = TenantAccessGuard.resolveWriteTenant(event.getTenantId(), PurchaseApplicationService.DEFAULT_TENANT_ID);

        Optional<Product> product = productRepository.findBySkuCode(skuCode);
        if (product.isEmpty()) {
            log.warn("[Replenishment] SKU {} 没有对应商品（仓库={}），跳过建单", skuCode, event.getWarehouseId());
            return Optional.empty();
        }
        // 事件线程里没有会话上下文，租户归属只能按事件载荷与商品行自己判（ISS-050 的同一条口径）
        String productTenantId = product.get().getTenantId();
        if (productTenantId != null && !productTenantId.isBlank() && !productTenantId.equals(tenantId)) {
            log.warn("[Replenishment] SKU {} 归属租户 {} 却收到租户 {} 的缺货事件，跳过建单",
                    skuCode, productTenantId, tenantId);
            return Optional.empty();
        }
        BigDecimal costPrice = product.get().findSkuByCode(skuCode)
                .map(SKU::getPrice)
                .map(price -> price.getCostPrice())
                .orElse(null);
        if (costPrice == null || costPrice.signum() <= 0) {
            log.warn("[Replenishment] SKU {} 没有有效成本价（仓库={} 现存={} 阈值={}），只发预警不建单",
                    skuCode, event.getWarehouseId(), event.getCurrentQuantity(), event.getThreshold());
            return Optional.empty();
        }

        int suggestedQuantity = Math.max(MIN_SUGGESTED_QUANTITY, event.getThreshold() - event.getCurrentQuantity());
        PurchaseOrderItem item = PurchaseOrderItem.create(skuCode, product.get().getName(), suggestedQuantity, costPrice);
        PurchaseOrder purchaseOrder = purchaseApplicationService.createPurchaseOrder(tenantId, SUPPLIER_ID, SUPPLIER_NAME,
                event.getWarehouseId(), List.of(item));
        log.info("[Replenishment] 为 SKU {} 自动创建补货采购单 {}：数量={} 单价={}",
                skuCode, purchaseOrder.getPurchaseOrderId(), suggestedQuantity, costPrice);
        return Optional.of(purchaseOrder);
    }
}
