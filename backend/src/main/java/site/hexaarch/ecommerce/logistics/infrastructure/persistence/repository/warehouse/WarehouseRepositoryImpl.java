package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.warehouse;

import site.hexaarch.ecommerce.logistics.domain.common.BaseAggregateRoot;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.Warehouse;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.WarehouseRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.warehouse.WarehouseMapper;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.List;
import java.util.Optional;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.warehouse.WarehouseJpaRepository;

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
        Warehouse savedWarehouse = warehouseMapper.toDomainAggregate(savedEntity);
        // save 重建的聚合副本不携带领域事件，补回后应用服务才能发布（ISS-062）
        BaseAggregateRoot.carryOverEvents(warehouse, savedWarehouse);
        return savedWarehouse;
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
    public Optional<Warehouse> findFirstByTenantId(String tenantId) {
        return warehouseJpaRepository.findFirstByTenantIdOrderByIdAsc(tenantId)
                .map(warehouseMapper::toDomainAggregate);
    }

    @Override
    public List<Warehouse> listByTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return List.of();
        }
        return warehouseJpaRepository.findAllByTenantIdOrderByIdAsc(tenantId)
                .stream()
                .map(warehouseMapper::toDomainAggregate)
                .toList();
    }

    @Override
    public PageResult<Warehouse> search(String tenantId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        var pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = (tenantId == null || tenantId.isBlank())
                ? warehouseJpaRepository.findAll(pageable)
                : warehouseJpaRepository.findByTenantId(tenantId, pageable);
        List<Warehouse> warehouses = result.getContent().stream()
                .map(warehouseMapper::toDomainAggregate)
                .toList();
        return new PageResult<>(warehouses, result.getTotalElements(), result.getNumber(), result.getSize());
    }

    @Override
    public void delete(String id) {
        warehouseJpaRepository.deleteById(id);
    }
}
