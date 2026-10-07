package site.hexaarch.ecommerce.logistics.domain.warehouse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
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

        // 仓库编码唯一性校验
        warehouseRepository.findByWarehouseCode(warehouseCode.trim()).ifPresent(existing -> {
            throw new BusinessException("仓库编码已存在: " + warehouseCode);
        });

        // 创建仓库
        Warehouse warehouse = Warehouse.create(tenantId, warehouseName, warehouseCode, address, capacity);

        // 保存仓库
        return warehouseRepository.save(warehouse);
    }

    /**
     * 更新仓库信息。
     *
     * @param warehouseId   仓库ID
     * @param warehouseName 仓库名称
     * @param warehouseCode 仓库代码
     * @param address       仓库地址
     * @param capacity      仓库容量
     * @return 更新后的仓库
     */
    public Warehouse updateWarehouse(String warehouseId, String warehouseName, String warehouseCode, String address, int capacity) {
        // 查找仓库
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new EntityNotFoundException("仓库不存在: " + warehouseId));

        // 业务逻辑：验证仓库信息
        validateWarehouseInfo(warehouseName, warehouseCode, address, capacity);

        // 仓库编码唯一性校验（排除自身）
        warehouseRepository.findByWarehouseCode(warehouseCode.trim())
                .filter(existing -> !existing.getWarehouseId().equals(warehouseId))
                .ifPresent(existing -> {
                    throw new BusinessException("仓库编码已存在: " + warehouseCode);
                });

        // 更新仓库信息
        Warehouse updatedWarehouse = warehouse.toBuilder()
                .warehouseName(warehouseName.trim())
                .warehouseCode(warehouseCode.trim())
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
                .orElseThrow(() -> new EntityNotFoundException("仓库不存在: " + warehouseId));

        // 库位编码唯一性校验
        boolean duplicated = warehouse.getLocations() != null && warehouse.getLocations().stream()
                .anyMatch(existing -> existing.getLocationCode() != null
                        && existing.getLocationCode().equals(location.getLocationCode()));
        if (duplicated) {
            throw new BusinessException("库位编码已存在: " + location.getLocationCode());
        }

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
            throw new ValidationException("仓库名称不能为空");
        }
        if (warehouseCode == null || warehouseCode.isEmpty()) {
            throw new ValidationException("仓库编码不能为空");
        }

        if (address == null || address.isEmpty()) {
            throw new ValidationException("仓库地址不能为空");
        }

        if (capacity <= 0) {
            throw new ValidationException("仓库容量必须大于0");
        }
    }
}
