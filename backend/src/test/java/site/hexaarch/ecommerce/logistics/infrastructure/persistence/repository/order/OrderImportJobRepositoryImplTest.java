package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import site.hexaarch.ecommerce.logistics.domain.order.aggregate.OrderImportJob;
import site.hexaarch.ecommerce.logistics.domain.order.entity.OrderImportError;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.order.OrderImportJobErrorJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.order.OrderImportJobJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.order.OrderImportJobJpaRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 导入任务仓储持久化契约测试（ISS-067）：
 * {@code errors} 关联为 cascade=ALL + orphanRemoval=true，对已存在（托管）实体的更新
 * 必须原地修改集合（clear+addAll），绝不允许整体替换集合引用，否则 Hibernate 在 flush 时抛
 * "A collection with orphan deletion was no longer referenced by the owning entity instance"。
 */
class OrderImportJobRepositoryImplTest {

    @Mock
    private OrderImportJobJpaRepository jpaRepository;

    private OrderImportJobRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        repository = new OrderImportJobRepositoryImpl(jpaRepository);
    }

    @Test
    void shouldKeepErrorsCollectionReferenceWhenJobAlreadyPersisted() {
        OrderImportJobJpaEntity managed = new OrderImportJobJpaEntity();
        managed.setId("job-1");
        List<OrderImportJobErrorJpaEntity> loadedErrors = new ArrayList<>();
        managed.setErrors(loadedErrors);
        when(jpaRepository.findById("job-1")).thenReturn(Optional.of(managed));

        OrderImportJob job = OrderImportJob.restore("job-1", "default-tenant", "FILE", null, "orders.csv",
                java.time.LocalDateTime.now(), null,
                site.hexaarch.ecommerce.logistics.domain.product.valueobject.ImportJobStatus.RUNNING,
                2, 0, 0, 0, null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        job.recordFailure(OrderImportError.of(1, "EXT-1", null, null, "平台订单号为空"));

        repository.save(job);

        ArgumentCaptor<OrderImportJobJpaEntity> captor = ArgumentCaptor.forClass(OrderImportJobJpaEntity.class);
        verify(jpaRepository, times(1)).save(captor.capture());
        OrderImportJobJpaEntity saved = captor.getValue();
        assertSame(loadedErrors, saved.getErrors(), "更新托管实体时不得替换 errors 集合引用");
        assertEquals(1, saved.getErrors().size());
        assertEquals("job-1", saved.getErrors().get(0).getJob().getId());
    }

    @Test
    void shouldSetFreshCollectionWhenJobIsTransient() {
        when(jpaRepository.findById(any())).thenReturn(Optional.empty());

        OrderImportJob job = OrderImportJob.create("default-tenant", "FILE", null, "orders.csv");
        job.start(1);
        job.recordFailure(OrderImportError.of(1, "EXT-1", null, null, "失败原因"));
        job.complete();

        repository.save(job);

        ArgumentCaptor<OrderImportJobJpaEntity> captor = ArgumentCaptor.forClass(OrderImportJobJpaEntity.class);
        verify(jpaRepository, times(1)).save(captor.capture());
        assertEquals(1, captor.getValue().getErrors().size());
    }
}
