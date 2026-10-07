package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.customer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.customer.aggregate.CustomerImportJob;
import site.hexaarch.ecommerce.logistics.domain.customer.entity.CustomerImportError;
import site.hexaarch.ecommerce.logistics.domain.customer.repository.CustomerImportJobRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.customer.CustomerImportJobErrorJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.customer.CustomerImportJobJpaEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.customer.CustomerImportJobJpaRepository;

/**
 * 客户导入任务仓储实现
 *
 * @author kenyon
 */
@Repository
@RequiredArgsConstructor
public class CustomerImportJobRepositoryImpl implements CustomerImportJobRepository {

    private final CustomerImportJobJpaRepository customerImportJobJpaRepository;

    @Override
    @org.springframework.transaction.annotation.Transactional
    public CustomerImportJob save(CustomerImportJob job) {
        CustomerImportJobJpaEntity entity = customerImportJobJpaRepository.findById(job.getId())
                .orElseGet(CustomerImportJobJpaEntity::new);
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
        entity.setCreatedCustomerIds(toJson(job.getCreatedCustomerIds()));
        entity.setUpdatedCustomerIds(toJson(job.getUpdatedCustomerIds()));

        List<CustomerImportJobErrorJpaEntity> errors = new ArrayList<>();
        for (CustomerImportError error : job.getErrors()) {
            CustomerImportJobErrorJpaEntity errorEntity = new CustomerImportJobErrorJpaEntity();
            errorEntity.setId(UUID.randomUUID().toString());
            errorEntity.setJob(entity);
            errorEntity.setRowNumber(error.getRowNumber());
            errorEntity.setExternalBuyerId(truncate(error.getExternalBuyerId(), 128));
            errorEntity.setCustomerName(truncate(error.getCustomerName(), 128));
            errorEntity.setField(error.getField());
            errorEntity.setMessage(truncate(error.getMessage(), 1000));
            errors.add(errorEntity);
        }
        entity.setErrors(errors);

        customerImportJobJpaRepository.save(entity);
        return job;
    }

    @Override
    public Optional<CustomerImportJob> findById(String jobId) {
        return customerImportJobJpaRepository.findById(jobId).map(this::toDomain);
    }

    @Override
    public PageResult<CustomerImportJob> findByTenantId(String tenantId, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "startedAt"));
        var result = tenantId == null
                ? customerImportJobJpaRepository.findAll(pageable)
                : customerImportJobJpaRepository.findAll(
                        (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId),
                        pageable);
        List<CustomerImportJob> jobs = new ArrayList<>(result.getNumberOfElements());
        for (CustomerImportJobJpaEntity entity : result.getContent()) {
            jobs.add(toDomain(entity));
        }
        return new PageResult<>(jobs, result.getTotalElements(), result.getNumber(), result.getSize());
    }

    private CustomerImportJob toDomain(CustomerImportJobJpaEntity entity) {
        List<CustomerImportError> errors = new ArrayList<>();
        if (entity.getErrors() != null) {
            for (CustomerImportJobErrorJpaEntity errorEntity : entity.getErrors()) {
                errors.add(CustomerImportError.of(errorEntity.getRowNumber(), errorEntity.getExternalBuyerId(),
                        errorEntity.getCustomerName(), errorEntity.getField(), errorEntity.getMessage()));
            }
        }
        return CustomerImportJob.restore(entity.getId(), entity.getTenantId(), entity.getPlatform(),
                entity.getIntegrationId(), entity.getFileName(), entity.getStartedAt(), entity.getFinishedAt(),
                entity.getStatus(), entity.getTotalCount(), entity.getSuccessCount(), entity.getFailureCount(),
                entity.getSkippedCount(), entity.getMessage(), errors,
                fromJson(entity.getCreatedCustomerIds()), fromJson(entity.getUpdatedCustomerIds()));
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
