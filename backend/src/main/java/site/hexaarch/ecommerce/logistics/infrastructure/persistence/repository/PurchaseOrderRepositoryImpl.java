package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository;

import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.domain.purchase.aggregate.PurchaseOrder;
import site.hexaarch.ecommerce.logistics.domain.purchase.repository.PurchaseOrderRepository;
import site.hexaarch.ecommerce.logistics.domain.purchase.valueobject.PurchaseStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.purchase.PurchaseOrderMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.purchase.PurchaseOrderJpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 采购单仓储的实现类，使用JPA进行持久化操作。
 *
 * @author kenyon
 */
@Repository
public class PurchaseOrderRepositoryImpl implements PurchaseOrderRepository {
    private final PurchaseOrderJpaRepository purchaseOrderJpaRepository;
    private final PurchaseOrderMapper purchaseOrderMapper;

    /**
     * 构造函数，注入JPA仓库和映射器。
     *
     * @param purchaseOrderJpaRepository 采购单JPA仓库
     * @param purchaseOrderMapper        采购单映射器
     */
    public PurchaseOrderRepositoryImpl(
            PurchaseOrderJpaRepository purchaseOrderJpaRepository,
            PurchaseOrderMapper purchaseOrderMapper) {
        this.purchaseOrderJpaRepository = purchaseOrderJpaRepository;
        this.purchaseOrderMapper = purchaseOrderMapper;
    }

    /**
     * 保存采购单。
     *
     * @param purchaseOrder 采购单实例
     */
    @Override
    public void save(PurchaseOrder purchaseOrder) {
        var purchaseOrderJpaEntity = purchaseOrderMapper.toJpaEntity(purchaseOrder);
        purchaseOrderJpaRepository.save(purchaseOrderJpaEntity);
    }

    /**
     * 根据采购单号查找采购单。
     *
     * @param purchaseOrderId 采购单号
     * @return 采购单实例，若不存在则返回Optional.empty()
     */
    @Override
    public Optional<PurchaseOrder> findById(String purchaseOrderId) {
        return purchaseOrderJpaRepository.findById(purchaseOrderId)
                .map(purchaseOrderMapper::toDomainAggregate);
    }

    /**
     * 根据状态查找采购单。
     *
     * @param status 采购单状态
     * @return 采购单列表
     */
    @Override
    public List<PurchaseOrder> findByStatus(PurchaseStatus status) {
        return purchaseOrderJpaRepository.findByStatus(status.getCode())
                .stream()
                .map(purchaseOrderMapper::toDomainAggregate)
                .toList();
    }

    /**
     * 根据供应商ID查找采购单。
     *
     * @param supplierId 供应商ID
     * @return 采购单列表
     */
    @Override
    public List<PurchaseOrder> findBySupplierId(String supplierId) {
        return purchaseOrderJpaRepository.findBySupplierId(supplierId)
                .stream()
                .map(purchaseOrderMapper::toDomainAggregate)
                .toList();
    }

    /**
     * 根据仓库ID查找采购单。
     *
     * @param warehouseId 仓库ID
     * @return 采购单列表
     */
    @Override
    public List<PurchaseOrder> findByWarehouseId(String warehouseId) {
        return purchaseOrderJpaRepository.findByWarehouseId(warehouseId)
                .stream()
                .map(purchaseOrderMapper::toDomainAggregate)
                .toList();
    }
}
