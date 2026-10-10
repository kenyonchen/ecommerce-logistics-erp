package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.purchase;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.purchase.aggregate.PurchaseOrder;
import site.hexaarch.ecommerce.logistics.domain.purchase.repository.PurchaseOrderRepository;
import site.hexaarch.ecommerce.logistics.domain.purchase.repository.PurchaseQuery;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.purchase.PurchaseOrderItemJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.purchase.PurchaseOrderJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.purchase.PurchaseOrderMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.purchase.PurchaseOrderJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.specification.purchase.PurchaseOrderSpecification;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 采购单仓储的实现类，使用JPA进行持久化操作。
 *
 * @author kenyon
 */
@Repository
public class PurchaseOrderRepositoryImpl implements PurchaseOrderRepository {
    private static final int MAX_PAGE_SIZE = 200;

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
        // 采购项的关联由 PurchaseOrderItemJpaEntity 持有，必须回填，否则 purchase_order_id 外键为空
        if (purchaseOrderJpaEntity.getItems() != null) {
            purchaseOrderJpaEntity.getItems().forEach(item -> item.setPurchaseOrder(purchaseOrderJpaEntity));
        }
        purchaseOrderJpaRepository.save(purchaseOrderJpaEntity);
    }

    /**
     * 在指定租户内按采购单号查找采购单。
     *
     * @param tenantId        租户ID
     * @param purchaseOrderId 采购单号
     * @return 采购单实例，若不存在或不属于该租户则返回Optional.empty()
     */
    @Override
    public Optional<PurchaseOrder> findById(String tenantId, String purchaseOrderId) {
        if (tenantId == null || tenantId.isBlank()) {
            // 没有租户上下文时不放宽为全表读取，避免跨租户越权（ISS-050）
            return Optional.empty();
        }
        return purchaseOrderJpaRepository.findByTenantIdAndPurchaseOrderId(tenantId.trim(), purchaseOrderId)
                .map(purchaseOrderMapper::toDomainAggregate);
    }

    /**
     * 分页 + 条件查询采购单，按创建时间倒序。
     *
     * @param query 查询条件
     * @param page  页码（从0开始）
     * @param size  每页条数
     * @return 分页结果
     */
    @Override
    public PageResult<PurchaseOrder> search(PurchaseQuery query, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        var pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = purchaseOrderJpaRepository.findAll(PurchaseOrderSpecification.withQuery(query), pageable);
        List<PurchaseOrder> purchaseOrders = new ArrayList<>(result.getNumberOfElements());
        for (PurchaseOrderJpaEntity entity : result.getContent()) {
            purchaseOrders.add(purchaseOrderMapper.toDomainAggregate(entity));
        }
        return new PageResult<>(purchaseOrders, result.getTotalElements(), result.getNumber(), result.getSize());
    }

    /**
     * 统计指向某张仓库的采购单数量（删仓库的引用守卫，ISS-142）。
     *
     * @param warehouseId 仓库ID
     * @return 指向该仓库的采购单数量
     */
    @Override
    public long countByWarehouseId(String warehouseId) {
        if (warehouseId == null || warehouseId.isBlank()) {
            return 0L;
        }
        return purchaseOrderJpaRepository.countByWarehouseId(warehouseId.trim());
    }
}
