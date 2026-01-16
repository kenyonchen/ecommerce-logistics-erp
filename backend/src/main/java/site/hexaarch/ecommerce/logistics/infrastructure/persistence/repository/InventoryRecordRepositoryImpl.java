package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository;

import site.hexaarch.ecommerce.logistics.domain.warehouse.entity.InventoryRecord;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.InventoryRecordRepository;
import site.hexaarch.ecommerce.logistics.domain.warehouse.valueobject.InventoryStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.WarehouseMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.InventoryRecordJpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 库存记录仓库实现类，使用JPA实现持久化。
 *
 * @author kenyon
 */
public class InventoryRecordRepositoryImpl implements InventoryRecordRepository {
    private final InventoryRecordJpaRepository inventoryRecordJpaRepository;
    private final WarehouseMapper warehouseMapper;

    // 手动添加构造函数，避免Lombok注解问题
    public InventoryRecordRepositoryImpl(InventoryRecordJpaRepository inventoryRecordJpaRepository, WarehouseMapper warehouseMapper) {
        this.inventoryRecordJpaRepository = inventoryRecordJpaRepository;
        this.warehouseMapper = warehouseMapper;
    }

    @Override
    public InventoryRecord save(InventoryRecord inventoryRecord) {
        var inventoryRecordJpaEntity = warehouseMapper.toJpaEntity(inventoryRecord);
        var savedEntity = inventoryRecordJpaRepository.save(inventoryRecordJpaEntity);
        return warehouseMapper.toDomainEntity(savedEntity);
    }

    @Override
    public Optional<InventoryRecord> findById(String id) {
        return inventoryRecordJpaRepository.findById(id)
                .map(warehouseMapper::toDomainEntity);
    }

    @Override
    public Optional<InventoryRecord> findByProductId(String productId) {
        return Optional.empty();
    }

    @Override
    public List<InventoryRecord> findByWarehouseId(String warehouseId) {
        return inventoryRecordJpaRepository.findByWarehouseId(warehouseId)
                .stream()
                .map(warehouseMapper::toDomainEntity)
                .toList();
    }

    @Override
    public List<InventoryRecord> findByInventoryStatus(InventoryStatus status) {
        return List.of();
    }

    @Override
    public List<InventoryRecord> findAll() {
        return inventoryRecordJpaRepository.findAll()
                .stream()
                .map(warehouseMapper::toDomainEntity)
                .toList();
    }

    @Override
    public void delete(String id) {
        inventoryRecordJpaRepository.deleteById(id);
    }
}
