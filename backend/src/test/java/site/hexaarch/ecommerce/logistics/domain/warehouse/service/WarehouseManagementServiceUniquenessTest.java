package site.hexaarch.ecommerce.logistics.domain.warehouse.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.Warehouse;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.WarehouseRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 仓库名称唯一性校验测试（ISS-116，docs/spec/warehouse.md BR-01 与验收条件 1）。
 * <p>
 * 修复前只校验编码：改名为已存在的仓库名称时冲突要到 flush 才由 {@code warehouse_name} 的 UNIQUE
 * 约束抛出，接口返回 500 SYSTEM_ERROR，用户看不到"重名"这个真实原因。
 * </p>
 *
 * @author kenyon
 */
@ExtendWith(MockitoExtension.class)
class WarehouseManagementServiceUniquenessTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @InjectMocks
    private WarehouseManagementService warehouseManagementService;

    private Warehouse warehouse(String id, String name, String code) {
        return Warehouse.builder()
                .warehouseId(id)
                .tenantId("tenant-1")
                .warehouseName(name)
                .warehouseCode(code)
                .address("深圳市科技园 1 号")
                .capacity(100)
                .active(true)
                .build();
    }

    @Test
    void 新建时名称撞号返回业务异常而不是落库() {
        when(warehouseRepository.findByWarehouseCode("WH-NEW")).thenReturn(Optional.empty());
        when(warehouseRepository.findByWarehouseName("上海仓"))
                .thenReturn(Optional.of(warehouse("wh-existing", "上海仓", "WH-OLD")));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                warehouseManagementService.createWarehouse("tenant-1", "上海仓", "WH-NEW", "杭州市文一西路 2 号", 50));

        assertEquals("仓库名称已存在: 上海仓", exception.getMessage());
        verify(warehouseRepository, never()).save(any(Warehouse.class));
    }

    @Test
    void 改名撞号返回业务异常而不是落库() {
        Warehouse current = warehouse("wh-1", "广州仓", "WH-1");
        when(warehouseRepository.findById("wh-1")).thenReturn(Optional.of(current));
        when(warehouseRepository.findByWarehouseCode("WH-1")).thenReturn(Optional.of(current));
        when(warehouseRepository.findByWarehouseName("上海仓"))
                .thenReturn(Optional.of(warehouse("wh-2", "上海仓", "WH-2")));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                warehouseManagementService.updateWarehouse("wh-1", "上海仓", "WH-1", "深圳市科技园 1 号", 200));

        assertEquals("仓库名称已存在: 上海仓", exception.getMessage());
        verify(warehouseRepository, never()).save(any(Warehouse.class));
    }

    @Test
    void 原名更新自己不被唯一性校验误拦() {
        Warehouse current = warehouse("wh-1", "广州仓", "WH-1");
        when(warehouseRepository.findById("wh-1")).thenReturn(Optional.of(current));
        when(warehouseRepository.findByWarehouseCode("WH-1")).thenReturn(Optional.of(current));
        when(warehouseRepository.findByWarehouseName("广州仓")).thenReturn(Optional.of(current));
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Warehouse updated = warehouseManagementService.updateWarehouse("wh-1", "广州仓", "WH-1",
                "深圳市科技园 1 号", 200);

        assertEquals(200, updated.getCapacity());
        verify(warehouseRepository).save(any(Warehouse.class));
    }

    @Test
    void 编码唯一性校验仍保留() {
        when(warehouseRepository.findByWarehouseCode("WH-DUP"))
                .thenReturn(Optional.of(warehouse("wh-other", "别仓", "WH-DUP")));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                warehouseManagementService.createWarehouse("tenant-1", "上海仓", "WH-DUP", "杭州市文一西路 2 号", 50));

        assertEquals("仓库编码已存在: WH-DUP", exception.getMessage());
        verify(warehouseRepository, never()).findByWarehouseName(anyString());
    }
}
