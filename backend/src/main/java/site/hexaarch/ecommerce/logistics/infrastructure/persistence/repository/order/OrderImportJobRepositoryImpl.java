package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.order;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.order.aggregate.OrderImportJob;
import site.hexaarch.ecommerce.logistics.domain.order.entity.OrderImportError;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderImportJobRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.order.OrderImportJobErrorJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.order.OrderImportJobJpaEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.order.OrderImportJobJpaRepository;

/**
 * 订单导入任务仓储实现
 *
 * @author kenyon
 */
@Repository
@RequiredArgsConstructor
public class OrderImportJobRepositoryImpl implements OrderImportJobRepository {

    private final OrderImportJobJpaRepository orderImportJobJpaRepository;

    @Override
    @org.springframework.transaction.annotation.Transactional
    public OrderImportJob save(OrderImportJob job) {
        OrderImportJobJpaEntity entity = orderImportJobJpaRepository.findById(job.getId())
                .orElseGet(OrderImportJobJpaEntity::new);
        entity.setId(job.getId());
        entity.setTenantId(job.getTenantId());
        entity.setPlatform(job.getPlatform());
        entity.setIntegrationId(job.getIntegrationId());
        entity.setFileName(job.getFileName());
        entity.setStatus(job.getStatus());
        entity.setTotalCount(job.getTotalCount());
        entity.setSuccessCount(job.getSuccessCount());
        entity.setFailureCount(job.getFailureCount());
        entity.setSkippedCount(job.getSkippedCount());
        entity.setMessage(truncate(job.getMessage(), 1000));
        entity.setStartedAt(job.getStartedAt());
        entity.setFinishedAt(job.getFinishedAt());
        entity.setCreatedOrderIds(toJson(job.getCreatedOrderIds()));
        entity.setUpdatedOrderIds(toJson(job.getUpdatedOrderIds()));

        List<OrderImportJobErrorJpaEntity> errors = new ArrayList<>();
        for (OrderImportError error : job.getErrors()) {
            OrderImportJobErrorJpaEntity errorEntity = new OrderImportJobErrorJpaEntity();
            errorEntity.setId(UUID.randomUUID().toString());
            errorEntity.setJob(entity);
            errorEntity.setRowNumber(error.getRowNumber());
            errorEntity.setExternalOrderId(truncate(error.getExternalOrderId(), 128));
            errorEntity.setOrderNumber(truncate(error.getOrderNumber(), 128));
            errorEntity.setField(error.getField());
            errorEntity.setMessage(truncate(error.getMessage(), 1000));
            errors.add(errorEntity);
        }
        // errors 关联是 cascade=ALL + orphanRemoval=true：更新已托管实体必须原地修改集合，
        // 整体替换引用会让 Hibernate 在 flush 时抛"collection with orphan deletion no longer referenced"（ISS-067）
        if (entity.getErrors() == null) {
            entity.setErrors(errors);
        } else {
            entity.getErrors().clear();
            entity.getErrors().addAll(errors);
        }

        orderImportJobJpaRepository.save(entity);
        return job;
    }

    @Override
    public Optional<OrderImportJob> findById(String jobId) {
        return orderImportJobJpaRepository.findById(jobId).map(this::toDomain);
    }

    @Override
    public PageResult<OrderImportJob> findByTenantId(String tenantId, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "startedAt"));
        var result = tenantId == null
                ? orderImportJobJpaRepository.findAll(pageable)
                : orderImportJobJpaRepository.findAll(
                        (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId),
                        pageable);
        List<OrderImportJob> jobs = new ArrayList<>(result.getNumberOfElements());
        for (OrderImportJobJpaEntity entity : result.getContent()) {
            jobs.add(toDomain(entity));
        }
        return new PageResult<>(jobs, result.getTotalElements(), result.getNumber(), result.getSize());
    }

    private OrderImportJob toDomain(OrderImportJobJpaEntity entity) {
        List<OrderImportError> errors = new ArrayList<>();
        if (entity.getErrors() != null) {
            for (OrderImportJobErrorJpaEntity errorEntity : entity.getErrors()) {
                errors.add(OrderImportError.of(errorEntity.getRowNumber(), errorEntity.getExternalOrderId(),
                        errorEntity.getOrderNumber(), errorEntity.getField(), errorEntity.getMessage()));
            }
        }
        return OrderImportJob.restore(entity.getId(), entity.getTenantId(), entity.getPlatform(),
                entity.getIntegrationId(), entity.getFileName(), entity.getStartedAt(), entity.getFinishedAt(),
                entity.getStatus(), entity.getTotalCount(), entity.getSuccessCount(), entity.getFailureCount(),
                entity.getSkippedCount(), entity.getMessage(), errors,
                fromJson(entity.getCreatedOrderIds()), fromJson(entity.getUpdatedOrderIds()));
    }

    private String toJson(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return new ObjectMapper().writeValueAsString(values);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return new ObjectMapper().readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
