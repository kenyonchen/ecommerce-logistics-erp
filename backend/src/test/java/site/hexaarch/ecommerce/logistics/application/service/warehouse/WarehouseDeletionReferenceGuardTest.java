package site.hexaarch.ecommerce.logistics.application.service.warehouse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.purchase.repository.PurchaseOrderRepository;
import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.ShopWarehouse;
import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.Warehouse;
import site.hexaarch.ecommerce.logistics.domain.warehouse.entity.InventoryRecord;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.InventoryRecordRepository;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.ShopWarehouseRepository;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.WarehouseRepository;
import site.hexaarch.ecommerce.logistics.domain.warehouse.service.InventoryCountService;
import site.hexaarch.ecommerce.logistics.domain.warehouse.service.InventoryManagementService;
import site.hexaarch.ecommerce.logistics.domain.warehouse.service.WarehouseManagementService;
import site.hexaarch.ecommerce.logistics.domain.warehouse.valueobject.InventoryStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;
import site.hexaarch.ecommerce.logistics.infrastructure.security.TenantContext;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 删除仓库的引用守卫（ISS-142）。
 * <p>
 * 实测缺陷：{@code DELETE /api/warehouses/{id}} 只看"仓里还有没有库存"，不看"还有没有单据指向它"。
 * 采购单把收货仓删掉后照样留在库里（{@code purchase_orders.warehouse_id} 是普通 varchar 列，没有外键），
 * 于是 533 行采购单里 530 行的仓库指向一张已不存在的仓 —— 采购域没有删除端点，
 * 这条断链只能靠 SQL 手工修，报表与对账上都查不到收货仓。
 * </p>
 * <p>
 * 口径与 R-31（租户删除不被墓碑挡住但仍挡引用）、R-59（已过账分录挡删除）一致：
 * 有引用就回可读中文原因并拒绝，删除必须整体不生效（{@code @Transactional} 回滚，不留半态）。
 * </p>
 *
 * @author kenyon
 */
@ExtendWith(MockitoExtension.class)
class WarehouseDeletionReferenceGuardTest {

    private static final String TENANT = "tenant-1";

    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private InventoryRecordRepository inventoryRecordRepository;
    @Mock
    private WarehouseManagementService warehouseManagementService;
    @Mock
    private InventoryManagementService inventoryManagementService;
    @Mock
    private InventoryCountService inventoryCountService;
    @Mock
    private DomainEventPublisher domainEventPublisher;
    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;
    @Mock
    private ShopWarehouseRepository shopWarehouseRepository;

    @InjectMocks
    private WarehouseApplicationService service;

    private String warehouseId;

    @BeforeEach
    void setUp() {
        TenantContext.clearTenantId();
        Warehouse warehouse = Warehouse.create(TENANT, "华东仓", "WH-EAST", "上海市", 1000);
        warehouseId = warehouse.getWarehouseId();
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clearTenantId();
    }

    private InventoryRecord record(String sku, int quantity) {
        return InventoryRecord.builder()
                .inventoryId("INV-" + sku)
                .warehouseId(warehouseId)
                .productId(sku)
                .sku(sku)
                .quantity(quantity)
                .inventoryStatus(quantity > 0 ? InventoryStatus.NORMAL : InventoryStatus.SHORTAGE)
                .build();
    }

    private ShopWarehouse binding() {
        return ShopWarehouse.create(TENANT, "shopify", "ITG-1", "SHOP-1", "一号店",
                "PW-1", "平台仓一", "深圳市", false);
    }

    @Test
    @DisplayName("仓库被采购单引用时必须拒绝删除，并点名引用数量")
    void purchaseOrderReferenceBlocksDeletion() {
        when(inventoryRecordRepository.findByWarehouseId(warehouseId)).thenReturn(List.of());
        when(purchaseOrderRepository.countByWarehouseId(warehouseId)).thenReturn(7L);

        ValidationException exception = assertThrows(ValidationException.class,
                () -> service.deleteWarehouse(warehouseId));

        assertTrue(exception.getMessage().contains("采购单"),
                "拒绝原因要点名是哪类单据引用了它：" + exception.getMessage());
        assertTrue(exception.getMessage().contains("7"),
                "拒绝原因要给出引用数量：" + exception.getMessage());
        verify(warehouseRepository, never()).delete(anyString());
    }

    @Test
    @DisplayName("仓库已绑定店铺仓库时必须拒绝删除")
    void shopBindingReferenceBlocksDeletion() {
        when(inventoryRecordRepository.findByWarehouseId(warehouseId)).thenReturn(List.of());
        when(purchaseOrderRepository.countByWarehouseId(warehouseId)).thenReturn(0L);
        when(shopWarehouseRepository.findByLocalWarehouseId(warehouseId)).thenReturn(List.of(binding()));

        ValidationException exception = assertThrows(ValidationException.class,
                () -> service.deleteWarehouse(warehouseId));

        assertTrue(exception.getMessage().contains("店铺"),
                "拒绝原因要点名是店铺绑定：" + exception.getMessage());
        verify(warehouseRepository, never()).delete(anyString());
    }

    @Test
    @DisplayName("仍有库存的仓照旧被挡（引用守卫不得顶掉既有的库存守卫）")
    void stockStillBlocksDeletion() {
        when(inventoryRecordRepository.findByWarehouseId(warehouseId))
                .thenReturn(List.of(record("SKU-A", 5)));

        ValidationException exception = assertThrows(ValidationException.class,
                () -> service.deleteWarehouse(warehouseId));

        assertTrue(exception.getMessage().contains("库存"),
                "库存守卫的原有口径不能变：" + exception.getMessage());
        verify(warehouseRepository, never()).delete(anyString());
    }

    @Test
    @DisplayName("无库存、无引用时仓库正常删除，库存记录随仓清空")
    void unreferencedWarehouseIsDeleted() {
        when(inventoryRecordRepository.findByWarehouseId(warehouseId))
                .thenReturn(List.of(record("SKU-B", 0)));
        when(purchaseOrderRepository.countByWarehouseId(warehouseId)).thenReturn(0L);
        when(shopWarehouseRepository.findByLocalWarehouseId(warehouseId)).thenReturn(List.of());

        service.deleteWarehouse(warehouseId);

        verify(inventoryRecordRepository).delete("INV-SKU-B");
        verify(warehouseRepository).delete(warehouseId);
    }
}
