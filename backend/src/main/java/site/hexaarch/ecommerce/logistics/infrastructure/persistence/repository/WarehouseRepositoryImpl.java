package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository;

import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.Warehouse;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.WarehouseRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.WarehouseMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.WarehouseJpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 仓库仓库实现类，使用JPA实现持久化。
 *
 * @author kenyon
 */
public class WarehouseRepositoryImpl implements WarehouseRepository {
    private final WarehouseJpaRepository warehouseJpaRepository;
    private final WarehouseMapper warehouseMapper;

    // 手动添加构造函数，避免Lombok注解问题
    public WarehouseRepositoryImpl(WarehouseJpaRepository warehouseJpaRepository, WarehouseMapper warehouseMapper) {
        this.warehouseJpaRepository = warehouseJpaRepository;
        this.warehouseMapper = warehouseMapper;
    }

    @Override
    public Warehouse save(Warehouse warehouse) {
        var warehouseJpaEntity = warehouseMapper.toJpaEntity(warehouse);
        var savedEntity = warehouseJpaRepository.save(warehouseJpaEntity);
        return warehouseMapper.toDomainAggregate(savedEntity);
    }

    @Override
    public Optional<Warehouse> findById(String id) {
        return warehouseJpaRepository.findById(id)
                .map(warehouseMapper::toDomainAggregate);
    }

    @Override
    public Optional<Warehouse> findByWarehouseCode(String warehouseCode) {
        return warehouseJpaRepository.findByWarehouseCode(warehouseCode)
                .map(warehouseMapper::toDomainAggregate);
    }

    @Override
    public Optional<Warehouse> findByWarehouseName(String warehouseName) {
        return warehouseJpaRepository.findByWarehouseName(warehouseName)
                .map(warehouseMapper::toDomainAggregate);
    }

    @Override
    public List<Warehouse> findActiveWarehouses() {
        return warehouseJpaRepository.findByActiveTrue()
                .stream()
                .map(warehouseMapper::toDomainAggregate)
                .toList();
    }

    @Override
    public List<Warehouse> findAll() {
        return warehouseJpaRepository.findAll()
                .stream()
                .map(warehouseMapper::toDomainAggregate)
                .toList();
    }

    @Override
    public void delete(String id) {
        warehouseJpaRepository.deleteById(id);
    }
}
