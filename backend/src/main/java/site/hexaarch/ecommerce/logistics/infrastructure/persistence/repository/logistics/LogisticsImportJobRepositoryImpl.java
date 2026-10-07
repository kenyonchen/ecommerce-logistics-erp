package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.logistics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.logistics.aggregate.LogisticsImportJob;
import site.hexaarch.ecommerce.logistics.domain.logistics.entity.LogisticsImportError;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsImportJobRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.logistics.LogisticsImportJobErrorJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.logistics.LogisticsImportJobJpaEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.logistics.LogisticsImportJobJpaRepository;

/**
 * 物流单导入任务仓储实现
 *
 * @author kenyon
 */
@Repository
@RequiredArgsConstructor
public class LogisticsImportJobRepositoryImpl implements LogisticsImportJobRepository {

    private final LogisticsImportJobJpaRepository logisticsImportJobJpaRepository;

    @Override
    @Transactional
    public LogisticsImportJob save(LogisticsImportJob job) {
        LogisticsImportJobJpaEntity entity = logisticsImportJobJpaRepository.findById(job.getId())
                .orElseGet(LogisticsImportJobJpaEntity::new);
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
        entity.setCreatedLogisticsOrderIds(toJson(job.getCreatedLogisticsOrderIds()));
        entity.setUpdatedLogisticsOrderIds(toJson(job.getUpdatedLogisticsOrderIds()));

        List<LogisticsImportJobErrorJpaEntity> errors = new ArrayList<>();
        for (LogisticsImportError error : job.getErrors()) {
            LogisticsImportJobErrorJpaEntity errorEntity = new LogisticsImportJobErrorJpaEntity();
            errorEntity.setId(UUID.randomUUID().toString());
            errorEntity.setJob(entity);
            errorEntity.setRowNumber(error.getRowNumber());
            errorEntity.setExternalLogisticsId(truncate(error.getExternalLogisticsId(), 128));
            errorEntity.setOrderNumber(truncate(error.getOrderNumber(), 128));
            errorEntity.setField(error.getField());
            errorEntity.setMessage(truncate(error.getMessage(), 1000));
            errors.add(errorEntity);
        }
        entity.setErrors(errors);

        logisticsImportJobJpaRepository.save(entity);
        return job;
    }

    @Override
    public Optional<LogisticsImportJob> findById(String jobId) {
        return logisticsImportJobJpaRepository.findById(jobId).map(this::toDomain);
    }

    @Override
    public PageResult<LogisticsImportJob> findByTenantId(String tenantId, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "startedAt"));
        var result = tenantId == null
                ? logisticsImportJobJpaRepository.findAll(pageable)
                : logisticsImportJobJpaRepository.findAll(
                        (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId),
                        pageable);
        List<LogisticsImportJob> jobs = new ArrayList<>(result.getNumberOfElements());
        for (LogisticsImportJobJpaEntity entity : result.getContent()) {
            jobs.add(toDomain(entity));
        }
        return new PageResult<>(jobs, result.getTotalElements(), result.getNumber(), result.getSize());
    }

    private LogisticsImportJob toDomain(LogisticsImportJobJpaEntity entity) {
        List<LogisticsImportError> errors = new ArrayList<>();
        if (entity.getErrors() != null) {
            for (LogisticsImportJobErrorJpaEntity errorEntity : entity.getErrors()) {
                errors.add(LogisticsImportError.of(errorEntity.getRowNumber(), errorEntity.getExternalLogisticsId(),
                        errorEntity.getOrderNumber(), errorEntity.getField(), errorEntity.getMessage()));
            }
        }
        return LogisticsImportJob.restore(entity.getId(), entity.getTenantId(), entity.getPlatform(),
                entity.getIntegrationId(), entity.getFileName(), entity.getStartedAt(), entity.getFinishedAt(),
                entity.getStatus(), entity.getTotalCount(), entity.getSuccessCount(), entity.getFailureCount(),
                entity.getSkippedCount(), entity.getMessage(), errors,
                fromJson(entity.getCreatedLogisticsOrderIds()), fromJson(entity.getUpdatedLogisticsOrderIds()));
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
