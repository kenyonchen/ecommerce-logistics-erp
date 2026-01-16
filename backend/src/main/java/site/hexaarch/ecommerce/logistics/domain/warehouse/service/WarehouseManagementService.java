package site.hexaarch.ecommerce.logistics.domain.warehouse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.Warehouse;
import site.hexaarch.ecommerce.logistics.domain.warehouse.entity.Location;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.WarehouseRepository;

/**
 * 仓库管理服务，负责管理仓库信息，如创建、修改仓库。
 *
 * @author kenyon
 */
@Service
@RequiredArgsConstructor
public class WarehouseManagementService {
    private final WarehouseRepository warehouseRepository;

    /**
     * 创建仓库。
     *
     * @param tenantId      租户ID
     * @param warehouseName 仓库名称
     * @param warehouseCode 仓库代码
     * @param address       仓库地址
     * @param capacity      仓库容量
     * @return 创建的仓库
     */
    public Warehouse createWarehouse(String tenantId, String warehouseName, String warehouseCode, String address, int capacity) {
        // 业务逻辑：验证仓库信息
        validateWarehouseInfo(warehouseName, warehouseCode, address, capacity);

        // 创建仓库
        Warehouse warehouse = Warehouse.create(tenantId, warehouseName, address, capacity);

        // 保存仓库
        return warehouseRepository.save(warehouse);
    }

    /**
     * 更新仓库信息。
     *
     * @param warehouseId   仓库ID
     * @param warehouseName 仓库名称
     * @param address       仓库地址
     * @param capacity      仓库容量
     * @return 更新后的仓库
     */
    public Warehouse updateWarehouse(String warehouseId, String warehouseName, String warehouseCode, String address, int capacity) {
        // 查找仓库
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new IllegalArgumentException("Warehouse not found: " + warehouseId));

        // 业务逻辑：验证仓库信息
        validateWarehouseInfo(warehouseName, warehouseCode, address, capacity);

        // 更新仓库信息
        Warehouse updatedWarehouse = warehouse.toBuilder()
                .warehouseName(warehouseName)
                .warehouseCode(warehouseCode)
                .address(address)
                .capacity(capacity)
                .build();

        // 保存仓库
        return warehouseRepository.save(updatedWarehouse);
    }

    /**
     * 添加库位。
     *
     * @param warehouseId 仓库ID
     * @param location    库位
     * @return 更新后的仓库
     */
    public Warehouse addLocation(String warehouseId, Location location) {
        // 查找仓库
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new IllegalArgumentException("Warehouse not found: " + warehouseId));

        // 添加库位
        warehouse.addLocation(location);

        // 保存仓库
        return warehouseRepository.save(warehouse);
    }

    /**
     * 验证仓库信息。
     *
     * @param warehouseName 仓库名称
     * @param warehouseCode 仓库代码
     * @param address       仓库地址
     * @param capacity      仓库容量
     */
    private void validateWarehouseInfo(String warehouseName, String warehouseCode, String address, int capacity) {
        if (warehouseName == null || warehouseName.isEmpty()) {
            throw new IllegalArgumentException("Warehouse name cannot be null or empty");
        }
        if (warehouseCode == null || warehouseCode.isEmpty()) {
            throw new IllegalArgumentException("Warehouse code cannot be null or empty");
        }

        if (address == null || address.isEmpty()) {
            throw new IllegalArgumentException("Warehouse address cannot be null or empty");
        }

        if (capacity <= 0) {
            throw new IllegalArgumentException("Warehouse capacity must be greater than 0");
        }
    }
}