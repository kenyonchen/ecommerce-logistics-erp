package site.hexaarch.ecommerce.logistics.application.service.purchase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import site.hexaarch.ecommerce.logistics.domain.product.aggregate.Product;
import site.hexaarch.ecommerce.logistics.domain.product.entity.SKU;
import site.hexaarch.ecommerce.logistics.domain.product.repository.ProductRepository;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ProductPrice;
import site.hexaarch.ecommerce.logistics.domain.purchase.aggregate.PurchaseOrder;
import site.hexaarch.ecommerce.logistics.domain.purchase.entity.PurchaseOrderItem;
import site.hexaarch.ecommerce.logistics.domain.warehouse.event.InventoryShortageEvent;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 缺货自动补货编排（BR-12 / ISS-143）。
 * <p>
 * 缺陷形状：消费端只调 4 参重载建"单头"，事件里带的 SKU 与现存量从未进入明细行。
 * 实测 366 张 {@code DEFAULT_SUPPLIER} 采购单里 366 张的 {@code purchase_order_items} 计数为 0，
 * 而 BR-09 的到货入库是逐行 {@code inventoryIn} ⇒ 空单到货等于什么都没入，这条链事实上不可用。
 * </p>
 * <p>
 * 本类用例逐条钉住"必须有明细行"与"定价取不到就不建单"两侧（R-31 一正一负）：
 * 单价一律取自 SKU 的 {@code costPrice}，绝不拿售价或猜数顶替（R-58 不凭空编金额）。
 * </p>
 *
 * @author kenyon
 */
class ReplenishmentApplicationServiceTest {

    private static final String TENANT = "tenant-1";
    private static final String WAREHOUSE = "wh-1";
    private static final String PRODUCT_ID = "product-1";
    private static final String SKU_CODE = "SKU-001";
    private static final String PRODUCT_NAME = "无线鼠标";
    private static final String SUPPLIER_ID = "DEFAULT_SUPPLIER";

    @Mock
    private ProductRepository productRepository;

    @Mock
    private PurchaseApplicationService purchaseApplicationService;

    private ReplenishmentApplicationService replenishmentApplicationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        replenishmentApplicationService = new ReplenishmentApplicationService(productRepository, purchaseApplicationService);
    }

    @Test
    @DisplayName("缺货建单必须带一条明细行：数量=阈值-现存量，单价=SKU 成本价")
    void shortageCreatesPurchaseOrderWithPricedItemLine() {
        when(productRepository.findBySkuCode(SKU_CODE)).thenReturn(Optional.of(product(SKU_CODE, "8.00", TENANT)));
        when(purchaseApplicationService.createPurchaseOrder(eq(TENANT), eq(SUPPLIER_ID), anyString(), eq(WAREHOUSE), anyList()))
                .thenReturn(purchaseOrder());

        Optional<PurchaseOrder> created = replenishmentApplicationService.replenishForShortage(shortage(2, 10));

        ArgumentCaptor<List<PurchaseOrderItem>> items = ArgumentCaptor.forClass(List.class);
        verify(purchaseApplicationService).createPurchaseOrder(eq(TENANT), eq(SUPPLIER_ID), anyString(), eq(WAREHOUSE), items.capture());
        // 阈值 10、现存 2 ⇒ 建议补 8；单价取 SKU 成本价 8.00
        assertEquals(1, items.getValue().size(), "明细行数：空单进不了 BR-09 的逐行入库（ISS-143）");
        PurchaseOrderItem item = items.getValue().get(0);
        assertEquals(SKU_CODE, item.getSkuCode());
        assertEquals(PRODUCT_NAME, item.getProductName());
        assertEquals(8, item.getQuantity());
        assertEquals(new BigDecimal("8.00"), item.getUnitPrice());
        assertTrue(created.isPresent(), "补货单已落库，出参要能把单交回调用方留痕");
    }

    @Test
    @DisplayName("建议量下限为 1：存量已达阈值也不能建出数量 0 的行")
    void suggestedQuantityFloorsAtOne() {
        when(productRepository.findBySkuCode(SKU_CODE)).thenReturn(Optional.of(product(SKU_CODE, "8.00", TENANT)));
        when(purchaseApplicationService.createPurchaseOrder(eq(TENANT), eq(SUPPLIER_ID), anyString(), eq(WAREHOUSE), anyList()))
                .thenReturn(purchaseOrder());

        replenishmentApplicationService.replenishForShortage(shortage(10, 10));

        ArgumentCaptor<List<PurchaseOrderItem>> items = ArgumentCaptor.forClass(List.class);
        verify(purchaseApplicationService).createPurchaseOrder(eq(TENANT), eq(SUPPLIER_ID), anyString(), eq(WAREHOUSE), items.capture());
        // quantity<=0 会让 PurchaseOrderItem.create 抛 ValidationException，整张单建不出来
        assertEquals(1, items.getValue().get(0).getQuantity());
    }

    @Test
    @DisplayName("SKU 无成本价时只发预警不建单：不拿售价或猜数充当采购单价")
    void missingCostPriceSkipsOrderInsteadOfInventingOne() {
        when(productRepository.findBySkuCode(SKU_CODE)).thenReturn(Optional.of(product(SKU_CODE, null, TENANT)));

        assertTrue(replenishmentApplicationService.replenishForShortage(shortage(2, 10)).isEmpty());
        verifyNoInteractions(purchaseApplicationService);
    }

    @Test
    @DisplayName("成本价为 0 时同样不建单：零元采购单会入一条零元应付")
    void nonPositiveCostPriceSkipsOrder() {
        when(productRepository.findBySkuCode(SKU_CODE)).thenReturn(Optional.of(product(SKU_CODE, "0.00", TENANT)));

        assertTrue(replenishmentApplicationService.replenishForShortage(shortage(2, 10)).isEmpty());
        verifyNoInteractions(purchaseApplicationService);
    }

    @Test
    @DisplayName("事件里的 SKU 查不到商品时不建单：没有可入库的明细就没有单")
    void unknownSkuSkipsOrder() {
        when(productRepository.findBySkuCode(SKU_CODE)).thenReturn(Optional.empty());

        assertTrue(replenishmentApplicationService.replenishForShortage(shortage(2, 10)).isEmpty());
        verifyNoInteractions(purchaseApplicationService);
    }

    @Test
    @DisplayName("SKU 编码缺失时不建单：无法定价也无法入库")
    void blankSkuSkipsOrder() {
        assertTrue(replenishmentApplicationService.replenishForShortage(shortageWithSku(null)).isEmpty());
        assertTrue(replenishmentApplicationService.replenishForShortage(shortageWithSku("  ")).isEmpty());

        verify(productRepository, never()).findBySkuCode(any());
        verifyNoInteractions(purchaseApplicationService);
    }

    @Test
    @DisplayName("商品归属别家租户时不建单：事件线程没有会话上下文，守卫只能按载荷判")
    void otherTenantProductSkipsOrder() {
        when(productRepository.findBySkuCode(SKU_CODE)).thenReturn(Optional.of(product(SKU_CODE, "8.00", "tenant-2")));

        assertTrue(replenishmentApplicationService.replenishForShortage(shortage(2, 10)).isEmpty());
        verifyNoInteractions(purchaseApplicationService);
    }

    @Test
    @DisplayName("补货单归属取自事件载荷的租户，缺省才回退历史默认桶")
    void orderTenantComesFromEventPayloadWithDefaultBucketFallback() {
        when(productRepository.findBySkuCode(SKU_CODE)).thenReturn(Optional.of(product(SKU_CODE, "8.00", "tenant-9")));
        when(purchaseApplicationService.createPurchaseOrder(anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenReturn(purchaseOrder());

        replenishmentApplicationService.replenishForShortage(shortageWithTenant("tenant-9"));
        verify(purchaseApplicationService).createPurchaseOrder(eq("tenant-9"), eq(SUPPLIER_ID), anyString(), eq(WAREHOUSE), anyList());

        when(productRepository.findBySkuCode(SKU_CODE))
                .thenReturn(Optional.of(product(SKU_CODE, "8.00", PurchaseApplicationService.DEFAULT_TENANT_ID)));
        replenishmentApplicationService.replenishForShortage(shortageWithTenant(null));
        verify(purchaseApplicationService).createPurchaseOrder(eq(PurchaseApplicationService.DEFAULT_TENANT_ID),
                eq(SUPPLIER_ID), anyString(), eq(WAREHOUSE), anyList());
    }

    private static InventoryShortageEvent shortage(int currentQuantity, int threshold) {
        return event(TENANT, SKU_CODE, currentQuantity, threshold);
    }

    private static InventoryShortageEvent shortageWithSku(String sku) {
        return event(TENANT, sku, 2, 10);
    }

    private static InventoryShortageEvent shortageWithTenant(String tenantId) {
        return event(tenantId, SKU_CODE, 2, 10);
    }

    private static InventoryShortageEvent event(String tenantId, String sku, int currentQuantity, int threshold) {
        return InventoryShortageEvent.builder()
                .warehouseId(WAREHOUSE)
                .tenantId(tenantId)
                .productId(PRODUCT_ID)
                .sku(sku)
                .currentQuantity(currentQuantity)
                .threshold(threshold)
                .build();
    }

    private static Product product(String skuCode, String costPrice, String tenantId) {
        SKU sku = SKU.builder()
                .skuCode(skuCode)
                .productId(PRODUCT_ID)
                .name(PRODUCT_NAME)
                .price(ProductPrice.builder()
                        .sellingPrice(new BigDecimal("19.90"))
                        .costPrice(costPrice == null ? null : new BigDecimal(costPrice))
                        .currency("CNY")
                        .build())
                .build();
        return Product.builder()
                .id(PRODUCT_ID)
                .tenantId(tenantId)
                .name(PRODUCT_NAME)
                .skus(List.of(sku))
                .active(true)
                .build();
    }

    private static PurchaseOrder purchaseOrder() {
        return PurchaseOrder.builder()
                .purchaseOrderId("PO-1")
                .tenantId(TENANT)
                .supplierId(SUPPLIER_ID)
                .warehouseId(WAREHOUSE)
                .build();
    }
}
