package site.hexaarch.ecommerce.logistics.application.service.warehouse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.Warehouse;
import site.hexaarch.ecommerce.logistics.domain.warehouse.entity.InventoryRecord;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.InventoryRecordRepository;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.WarehouseRepository;
import site.hexaarch.ecommerce.logistics.domain.warehouse.service.InventoryCountService;
import site.hexaarch.ecommerce.logistics.domain.warehouse.service.InventoryManagementService;
import site.hexaarch.ecommerce.logistics.domain.warehouse.service.WarehouseManagementService;
import site.hexaarch.ecommerce.logistics.domain.warehouse.valueobject.InventoryStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 发货出库仓选择测试（BR-04，ISS-100）。
 * <p>
 * 订单域没有出库仓字段，冒烟 3.11 实测过旧口径的后果：租户下第三个仓发货时，
 * 扣减落在"租户第一个仓"上，那个仓根本没有这个 SKU → PUT /orders/&#42;/status?status=SHIPPED 报
 * {@code ENTITY_NOT_FOUND 库存记录不存在}，发货被一条与本单无关的仓数据挡死。
 * </p>
 *
 * @author kenyon
 */
@ExtendWith(MockitoExtension.class)
class WarehouseApplicationServiceOutboundTest {

    private static final String TENANT = "tenant-1";
    private static final String SKU = "SKU-X";

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

    @InjectMocks
    private WarehouseApplicationService service;

    private Warehouse warehouseWithStock(String name, String sku, int quantity) {
        Warehouse warehouse = Warehouse.create(TENANT, name, "CODE-" + name, "深圳市", 1000);
        if (sku != null) {
            warehouse.addInventoryRecord(InventoryRecord.builder()
                    .warehouseId(warehouse.getWarehouseId())
                    .productId(sku)
                    .sku(sku)
                    .quantity(quantity)
                    .inventoryStatus(quantity > 0 ? InventoryStatus.NORMAL : InventoryStatus.SHORTAGE)
                    .build());
        }
        return warehouse;
    }

    @Test
    void picksTheWarehouseThatActuallyHoldsTheSku() {
        Warehouse empty = warehouseWithStock("EMPTY", null, 0);
        Warehouse holder = warehouseWithStock("HOLDER", SKU, 3);
        when(warehouseRepository.listByTenantId(TENANT)).thenReturn(List.of(empty, holder));

        assertEquals(holder.getWarehouseId(),
                service.resolveOutboundWarehouseId(TENANT, SKU, 1));
    }

    @Test
    void prefersTheWarehouseWithTheLargestCoveringStock() {
        Warehouse some = warehouseWithStock("SOME", SKU, 10);
        Warehouse more = warehouseWithStock("MORE", SKU, 20);
        when(warehouseRepository.listByTenantId(TENANT)).thenReturn(List.of(some, more));

        assertEquals(more.getWarehouseId(),
                service.resolveOutboundWarehouseId(TENANT, SKU, 15));
    }

    @Test
    void fallsBackToAHolderWithInsufficientStockSoShortageIsReportedNotNotFound() {
        Warehouse low = warehouseWithStock("LOW", SKU, 1);
        Warehouse empty = warehouseWithStock("EMPTY", null, 0);
        when(warehouseRepository.listByTenantId(TENANT)).thenReturn(List.of(empty, low));

        assertEquals(low.getWarehouseId(),
                service.resolveOutboundWarehouseId(TENANT, SKU, 5));
    }

    @Test
    void fallsBackToFirstWarehouseWhenNoTenantWarehouseCarriesTheSku() {
        Warehouse first = warehouseWithStock("FIRST", null, 0);
        when(warehouseRepository.listByTenantId(TENANT)).thenReturn(List.of(first));

        assertEquals(first.getWarehouseId(),
                service.resolveOutboundWarehouseId(TENANT, SKU, 5));
    }

    @Test
    void rejectsWhenTenantHasNoWarehouse() {
        when(warehouseRepository.listByTenantId(anyString())).thenReturn(List.of());

        ValidationException error = assertThrows(ValidationException.class,
                () -> service.resolveOutboundWarehouseId(TENANT, SKU, 1));
        assertEquals("当前租户下没有可用仓库，请先创建仓库", error.getMessage());
    }
}
