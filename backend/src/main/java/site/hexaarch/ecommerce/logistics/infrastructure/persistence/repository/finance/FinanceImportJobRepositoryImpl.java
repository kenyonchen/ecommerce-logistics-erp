package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.finance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.finance.aggregate.FinanceImportJob;
import site.hexaarch.ecommerce.logistics.domain.finance.entity.FinanceImportError;
import site.hexaarch.ecommerce.logistics.domain.finance.repository.FinanceImportJobRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.finance.FinanceImportJobErrorJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.finance.FinanceImportJobJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.finance.FinanceImportJobJpaRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 财务导入任务仓储实现
 *
 * @author kenyon
 */
@Repository
@RequiredArgsConstructor
public class FinanceImportJobRepositoryImpl implements FinanceImportJobRepository {

    private final FinanceImportJobJpaRepository financeImportJobJpaRepository;

    @Override
    @org.springframework.transaction.annotation.Transactional
    public FinanceImportJob save(FinanceImportJob job) {
        FinanceImportJobJpaEntity entity = financeImportJobJpaRepository.findById(job.getId())
                .orElseGet(FinanceImportJobJpaEntity::new);
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
        entity.setCreatedTransactionIds(toJson(job.getCreatedTransactionIds()));
        entity.setUpdatedTransactionIds(toJson(job.getUpdatedTransactionIds()));

        List<FinanceImportJobErrorJpaEntity> errors = new ArrayList<>();
        for (FinanceImportError error : job.getErrors()) {
            FinanceImportJobErrorJpaEntity errorEntity = new FinanceImportJobErrorJpaEntity();
            errorEntity.setId(UUID.randomUUID().toString());
            errorEntity.setJob(entity);
            errorEntity.setRowNumber(error.getRowNumber());
            errorEntity.setExternalTransactionId(truncate(error.getExternalTransactionId(), 128));
            errorEntity.setField(error.getField());
            errorEntity.setMessage(truncate(error.getMessage(), 1000));
            errors.add(errorEntity);
        }
        entity.setErrors(errors);

        financeImportJobJpaRepository.save(entity);
        return job;
    }

    @Override
    public Optional<FinanceImportJob> findById(String jobId) {
        return financeImportJobJpaRepository.findById(jobId).map(this::toDomain);
    }

    @Override
    public PageResult<FinanceImportJob> findByTenantId(String tenantId, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "startedAt"));
        var result = tenantId == null
                ? financeImportJobJpaRepository.findAll(pageable)
                : financeImportJobJpaRepository.findAll(
                        (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId),
                        pageable);
        List<FinanceImportJob> jobs = new ArrayList<>(result.getNumberOfElements());
        for (FinanceImportJobJpaEntity entity : result.getContent()) {
            jobs.add(toDomain(entity));
        }
        return new PageResult<>(jobs, result.getTotalElements(), result.getNumber(), result.getSize());
    }

    private FinanceImportJob toDomain(FinanceImportJobJpaEntity entity) {
        List<FinanceImportError> errors = new ArrayList<>();
        if (entity.getErrors() != null) {
            for (FinanceImportJobErrorJpaEntity errorEntity : entity.getErrors()) {
                errors.add(FinanceImportError.of(errorEntity.getRowNumber(), errorEntity.getExternalTransactionId(),
                        errorEntity.getField(), errorEntity.getMessage()));
            }
        }
        return FinanceImportJob.restore(entity.getId(), entity.getTenantId(), entity.getPlatform(),
                entity.getIntegrationId(), entity.getFileName(), entity.getStartedAt(), entity.getFinishedAt(),
                entity.getStatus(), entity.getTotalCount(), entity.getSuccessCount(), entity.getFailureCount(),
                entity.getSkippedCount(), entity.getMessage(), errors,
                fromJson(entity.getCreatedTransactionIds()), fromJson(entity.getUpdatedTransactionIds()));
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
