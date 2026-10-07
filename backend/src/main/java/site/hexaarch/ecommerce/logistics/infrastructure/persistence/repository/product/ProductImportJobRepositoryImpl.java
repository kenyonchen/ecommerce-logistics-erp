package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.product;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.product.aggregate.ProductImportJob;
import site.hexaarch.ecommerce.logistics.domain.product.entity.ProductImportError;
import site.hexaarch.ecommerce.logistics.domain.product.repository.ProductImportJobRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.product.ProductImportJobErrorJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.product.ProductImportJobJpaEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.product.ProductImportJobJpaRepository;

/**
 * 商品导入任务仓储实现
 *
 * @author kenyon
 */
@Repository
@RequiredArgsConstructor
public class ProductImportJobRepositoryImpl implements ProductImportJobRepository {

    private final ProductImportJobJpaRepository productImportJobJpaRepository;

    @Override
    @org.springframework.transaction.annotation.Transactional
    public ProductImportJob save(ProductImportJob job) {
        ProductImportJobJpaEntity entity = productImportJobJpaRepository.findById(job.getId())
                .orElseGet(ProductImportJobJpaEntity::new);
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
        entity.setCreatedProductIds(toJson(job.getCreatedProductIds()));
        entity.setUpdatedProductIds(toJson(job.getUpdatedProductIds()));

        List<ProductImportJobErrorJpaEntity> errors = new ArrayList<>();
        for (ProductImportError error : job.getErrors()) {
            ProductImportJobErrorJpaEntity errorEntity = new ProductImportJobErrorJpaEntity();
            errorEntity.setId(UUID.randomUUID().toString());
            errorEntity.setJob(entity);
            errorEntity.setRowNumber(error.getRowNumber());
            errorEntity.setExternalProductId(truncate(error.getExternalProductId(), 128));
            errorEntity.setSkuCode(truncate(error.getSkuCode(), 128));
            errorEntity.setField(error.getField());
            errorEntity.setMessage(truncate(error.getMessage(), 1000));
            errors.add(errorEntity);
        }
        entity.setErrors(errors);

        productImportJobJpaRepository.save(entity);
        return job;
    }

    @Override
    public Optional<ProductImportJob> findById(String jobId) {
        return productImportJobJpaRepository.findById(jobId).map(this::toDomain);
    }

    @Override
    public PageResult<ProductImportJob> findByTenantId(String tenantId, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "startedAt"));
        var result = tenantId == null
                ? productImportJobJpaRepository.findAll(pageable)
                : productImportJobJpaRepository.findAll(
                        (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId),
                        pageable);
        List<ProductImportJob> jobs = new ArrayList<>(result.getNumberOfElements());
        for (ProductImportJobJpaEntity entity : result.getContent()) {
            jobs.add(toDomain(entity));
        }
        return new PageResult<>(jobs, result.getTotalElements(), result.getNumber(), result.getSize());
    }

    private ProductImportJob toDomain(ProductImportJobJpaEntity entity) {
        List<ProductImportError> errors = new ArrayList<>();
        if (entity.getErrors() != null) {
            for (ProductImportJobErrorJpaEntity errorEntity : entity.getErrors()) {
                errors.add(ProductImportError.of(errorEntity.getRowNumber(), errorEntity.getExternalProductId(),
                        errorEntity.getSkuCode(), errorEntity.getField(), errorEntity.getMessage()));
            }
        }
        return ProductImportJob.restore(entity.getId(), entity.getTenantId(), entity.getPlatform(),
                entity.getIntegrationId(), entity.getFileName(), entity.getStartedAt(), entity.getFinishedAt(),
                entity.getStatus(), entity.getTotalCount(), entity.getSuccessCount(), entity.getFailureCount(),
                entity.getSkippedCount(), entity.getMessage(), errors,
                fromJson(entity.getCreatedProductIds()), fromJson(entity.getUpdatedProductIds()));
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
