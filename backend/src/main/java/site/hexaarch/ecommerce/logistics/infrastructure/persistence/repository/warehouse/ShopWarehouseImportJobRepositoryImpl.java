package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.warehouse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.ShopWarehouseImportJob;
import site.hexaarch.ecommerce.logistics.domain.warehouse.entity.ShopWarehouseImportError;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.ShopWarehouseImportJobRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.warehouse.ShopWarehouseImportJobErrorJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.warehouse.ShopWarehouseImportJobJpaEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.warehouse.ShopWarehouseImportJobJpaRepository;

/**
 * 店铺仓库导入任务仓储实现
 *
 * @author kenyon
 */
@Repository
@RequiredArgsConstructor
public class ShopWarehouseImportJobRepositoryImpl implements ShopWarehouseImportJobRepository {

    private final ShopWarehouseImportJobJpaRepository shopWarehouseImportJobJpaRepository;

    @Override
    @Transactional
    public ShopWarehouseImportJob save(ShopWarehouseImportJob job) {
        ShopWarehouseImportJobJpaEntity entity = shopWarehouseImportJobJpaRepository.findById(job.getId())
                .orElseGet(ShopWarehouseImportJobJpaEntity::new);
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
        entity.setCreatedShopWarehouseIds(toJson(job.getCreatedShopWarehouseIds()));
        entity.setUpdatedShopWarehouseIds(toJson(job.getUpdatedShopWarehouseIds()));

        List<ShopWarehouseImportJobErrorJpaEntity> errors = new ArrayList<>();
        for (ShopWarehouseImportError error : job.getErrors()) {
            ShopWarehouseImportJobErrorJpaEntity errorEntity = new ShopWarehouseImportJobErrorJpaEntity();
            errorEntity.setId(UUID.randomUUID().toString());
            errorEntity.setJob(entity);
            errorEntity.setRowNumber(error.getRowNumber());
            errorEntity.setShopCode(truncate(error.getShopCode(), 128));
            errorEntity.setPlatformWarehouseId(truncate(error.getPlatformWarehouseId(), 128));
            errorEntity.setField(error.getField());
            errorEntity.setMessage(truncate(error.getMessage(), 1000));
            errors.add(errorEntity);
        }
        entity.setErrors(errors);

        shopWarehouseImportJobJpaRepository.save(entity);
        return job;
    }

    @Override
    public Optional<ShopWarehouseImportJob> findById(String jobId) {
        return shopWarehouseImportJobJpaRepository.findById(jobId).map(this::toDomain);
    }

    @Override
    public PageResult<ShopWarehouseImportJob> findByTenantId(String tenantId, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "startedAt"));
        var result = tenantId == null
                ? shopWarehouseImportJobJpaRepository.findAll(pageable)
                : shopWarehouseImportJobJpaRepository.findAll(
                        (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId),
                        pageable);
        List<ShopWarehouseImportJob> jobs = new ArrayList<>(result.getNumberOfElements());
        for (ShopWarehouseImportJobJpaEntity entity : result.getContent()) {
            jobs.add(toDomain(entity));
        }
        return new PageResult<>(jobs, result.getTotalElements(), result.getNumber(), result.getSize());
    }

    private ShopWarehouseImportJob toDomain(ShopWarehouseImportJobJpaEntity entity) {
        List<ShopWarehouseImportError> errors = new ArrayList<>();
        if (entity.getErrors() != null) {
            for (ShopWarehouseImportJobErrorJpaEntity errorEntity : entity.getErrors()) {
                errors.add(ShopWarehouseImportError.of(errorEntity.getRowNumber(), errorEntity.getShopCode(),
                        errorEntity.getPlatformWarehouseId(), errorEntity.getField(), errorEntity.getMessage()));
            }
        }
        return ShopWarehouseImportJob.restore(entity.getId(), entity.getTenantId(), entity.getPlatform(),
                entity.getIntegrationId(), entity.getFileName(), entity.getStartedAt(), entity.getFinishedAt(),
                entity.getStatus(), entity.getTotalCount(), entity.getSuccessCount(), entity.getFailureCount(),
                entity.getSkippedCount(), entity.getMessage(), errors,
                fromJson(entity.getCreatedShopWarehouseIds()), fromJson(entity.getUpdatedShopWarehouseIds()));
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
