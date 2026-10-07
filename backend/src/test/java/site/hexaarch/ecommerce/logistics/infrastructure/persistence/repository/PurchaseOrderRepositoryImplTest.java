package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.purchase.aggregate.PurchaseOrder;
import site.hexaarch.ecommerce.logistics.domain.purchase.repository.PurchaseQuery;
import site.hexaarch.ecommerce.logistics.domain.purchase.valueobject.PurchaseStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.purchase.PurchaseOrderItemJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.purchase.PurchaseOrderJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.purchase.PurchaseOrderMapperImpl;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.purchase.PurchaseOrderJpaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.purchase.PurchaseOrderRepositoryImpl;

/**
 * 采购单仓储实现的测试，覆盖聚合与 JPA 实体之间的双向引用、字段回填与租户边界。
 *
 * @author kenyon
 */
class PurchaseOrderRepositoryImplTest {

    private PurchaseOrderJpaRepository jpaRepository;
    private PurchaseOrderRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        jpaRepository = mock(PurchaseOrderJpaRepository.class);
        repository = new PurchaseOrderRepositoryImpl(jpaRepository, new PurchaseOrderMapperImpl());
    }

    @Test
    void saveShouldLinkItemsToParentOtherwiseForeignKeyIsNull() {
        PurchaseOrder purchaseOrder = purchaseOrder();
        purchaseOrder.addItem("SKU-001", "商品A", 2, new BigDecimal("10.00"));

        repository.save(purchaseOrder);

        ArgumentCaptor<PurchaseOrderJpaEntity> captor = ArgumentCaptor.forClass(PurchaseOrderJpaEntity.class);
        verify(jpaRepository).save(captor.capture());
        PurchaseOrderJpaEntity saved = captor.getValue();
        assertEquals(1, saved.getItems().size());
        for (PurchaseOrderItemJpaEntity item : saved.getItems()) {
            assertSame(saved, item.getPurchaseOrder(), "采购项必须回填父单引用，否则 purchase_order_id 外键为空");
        }
    }

    @Test
    void saveShouldPersistTenantAndStatusCodeAtTheSameTime() {
        PurchaseOrder purchaseOrder = purchaseOrder();

        repository.save(purchaseOrder);

        ArgumentCaptor<PurchaseOrderJpaEntity> captor = ArgumentCaptor.forClass(PurchaseOrderJpaEntity.class);
        verify(jpaRepository).save(captor.capture());
        PurchaseOrderJpaEntity saved = captor.getValue();
        assertEquals("tenant-1", saved.getTenantId());
        assertEquals("pending", saved.getStatus());
    }

    @Test
    void saveShouldCarryApprovalFieldsIntoColumns() {
        PurchaseOrder purchaseOrder = purchaseOrder();
        purchaseOrder.approve("alice", "预算内");

        repository.save(purchaseOrder);

        ArgumentCaptor<PurchaseOrderJpaEntity> captor = ArgumentCaptor.forClass(PurchaseOrderJpaEntity.class);
        verify(jpaRepository).save(captor.capture());
        PurchaseOrderJpaEntity saved = captor.getValue();
        assertEquals("alice", saved.getApprovedBy());
        assertEquals("预算内", saved.getApprovalComment());
        assertFalse(saved.getApprovedAt() == null);
    }

    @Test
    void findByIdShouldQueryWithinTenantOnly() {
        PurchaseOrderJpaEntity entity = jpaEntity();
        when(jpaRepository.findByTenantIdAndPurchaseOrderId("tenant-1", "PO-1")).thenReturn(Optional.of(entity));

        Optional<PurchaseOrder> found = repository.findById("tenant-1", "PO-1");

        assertTrue(found.isPresent());
        assertEquals("PO-1", found.get().getPurchaseOrderId());
        assertEquals(new BigDecimal("100.00"), found.get().getTotalAmount());
    }

    @Test
    void findByIdWithoutTenantMustNotFallBackToGlobalRead() {
        // ISS-050 回归防护：没有租户条件时不允许读到任何采购单
        Optional<PurchaseOrder> found = repository.findById("  ", "PO-1");

        assertTrue(found.isEmpty());
        verify(jpaRepository, never()).findById(any());
        verify(jpaRepository, never()).findByTenantIdAndPurchaseOrderId(any(), any());
    }

    @Test
    void searchShouldClampPagingAndKeepCreatedAtOrder() {
        Page<PurchaseOrderJpaEntity> page = new PageImpl<>(List.of(jpaEntity()));
        when(jpaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PurchaseQuery query = new PurchaseQuery("tenant-1", "PO", "sup-1", "供应商", "wh-1",
                PurchaseStatus.PENDING, new BigDecimal("50.00"),
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 31, 23, 59, 59));
        PageResult<PurchaseOrder> result = repository.search(query, -1, 500);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(jpaRepository).findAll(any(Specification.class), pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        // 单页上限 200，避免前端把 size 当无界拉全表
        assertEquals(200, pageable.getValue().getPageSize());
        assertEquals("createdAt", pageable.getValue().getSort().iterator().next().getProperty());
        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getContent().size());
    }

    @Test
    void toDomainAggregateShouldKeepOriginalCreatedAt() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        PurchaseOrderJpaEntity entity = jpaEntity();
        entity.setCreatedAt(createdAt);

        PurchaseOrder purchaseOrder = new PurchaseOrderMapperImpl().toDomainAggregate(entity);

        assertEquals(createdAt, purchaseOrder.getCreatedAt());
    }

    private PurchaseOrder purchaseOrder() {
        return PurchaseOrder.builder()
                .purchaseOrderId("PO-1")
                .tenantId("tenant-1")
                .supplierId("sup-1")
                .supplierName("供应商")
                .warehouseId("wh-1")
                .status(PurchaseStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();
    }

    private PurchaseOrderJpaEntity jpaEntity() {
        PurchaseOrderJpaEntity entity = new PurchaseOrderJpaEntity();
        entity.setPurchaseOrderId("PO-1");
        entity.setTenantId("tenant-1");
        entity.setSupplierId("sup-1");
        entity.setSupplierName("供应商");
        entity.setWarehouseId("wh-1");
        entity.setStatus("pending");
        entity.setTotalAmount(new BigDecimal("100.00"));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setItems(List.of());
        return entity;
    }
}
