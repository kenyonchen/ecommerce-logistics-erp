package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.audit;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.domain.audit.entity.AuditLog;
import site.hexaarch.ecommerce.logistics.domain.audit.repository.AuditLogRepository;
import site.hexaarch.ecommerce.logistics.domain.audit.valueobject.AuditResult;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.audit.AuditLogJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.audit.AuditLogJpaRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 审计日志仓储实现：只追加，检索按租户/动作/结果收敛并按时间倒序。
 *
 * @author kenyon
 */
@Repository
public class AuditLogRepositoryImpl implements AuditLogRepository {

    private final AuditLogJpaRepository auditLogJpaRepository;

    public AuditLogRepositoryImpl(AuditLogJpaRepository auditLogJpaRepository) {
        this.auditLogJpaRepository = auditLogJpaRepository;
    }

    @Override
    public AuditLog save(AuditLog auditLog) {
        AuditLogJpaEntity entity = toEntity(auditLog);
        if (entity.getId() == null || entity.getId().isBlank()) {
            entity.setId(UUID.randomUUID().toString());
        }
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }
        return toDomain(auditLogJpaRepository.save(entity));
    }

    @Override
    public PageResult<AuditLog> search(String tenantId, String action, AuditResult result, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        var pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        var queryResult = auditLogJpaRepository.findAll(tenantAndActionAndResult(tenantId, action, result), pageable);
        return new PageResult<>(queryResult.getContent().stream().map(this::toDomain).toList(),
                queryResult.getTotalElements(), safePage, safeSize);
    }

    @Override
    public List<AuditLog> findRecent(String tenantId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        var pageable = PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt"));
        return auditLogJpaRepository.findAll(tenantAndActionAndResult(tenantId, null, null), pageable)
                .getContent().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long countFailuresSince(List<String> actions, String actorName, AuditResult result, LocalDateTime since) {
        if (actions == null || actions.isEmpty() || actorName == null || actorName.isBlank()) {
            return 0L;
        }
        return auditLogJpaRepository.countByActionInAndActorNameAndResultAndCreatedAtAfter(actions, actorName, result, since);
    }

    @Override
    public LocalDateTime findMostRecentSuccessTime(List<String> actions, String actorName) {
        if (actions == null || actions.isEmpty() || actorName == null || actorName.isBlank()) {
            return null;
        }
        Specification<AuditLogJpaEntity> successOfActor = (root, query, builder) -> builder.and(
                root.get("action").in(actions),
                builder.equal(root.get("actorName"), actorName),
                builder.equal(root.get("result"), AuditResult.SUCCESS));
        var pageable = PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt"));
        return auditLogJpaRepository.findAll(successOfActor, pageable).getContent().stream()
                .findFirst()
                .map(AuditLogJpaEntity::getCreatedAt)
                .orElse(null);
    }

    private Specification<AuditLogJpaEntity> tenantAndActionAndResult(String tenantId, String action, AuditResult result) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (tenantId != null && !tenantId.isBlank()) {
                predicates.add(builder.equal(root.get("tenantId"), tenantId));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(builder.equal(root.get("action"), action.trim()));
            }
            if (result != null) {
                predicates.add(builder.equal(root.get("result"), result));
            }
            return predicates.isEmpty() ? builder.conjunction() : builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private AuditLogJpaEntity toEntity(AuditLog auditLog) {
        AuditLogJpaEntity entity = new AuditLogJpaEntity();
        entity.setId(auditLog.getId());
        entity.setTenantId(auditLog.getTenantId());
        entity.setActorId(auditLog.getActorId());
        entity.setActorName(auditLog.getActorName());
        entity.setAction(auditLog.getAction());
        entity.setActionLabel(auditLog.getActionLabel());
        entity.setTargetType(auditLog.getTargetType());
        entity.setTargetId(auditLog.getTargetId());
        entity.setDetail(auditLog.getDetail());
        entity.setResult(auditLog.getResult() == null ? AuditResult.SUCCESS : auditLog.getResult());
        entity.setErrorMessage(auditLog.getErrorMessage());
        entity.setIp(auditLog.getIp());
        entity.setTraceId(auditLog.getTraceId());
        entity.setCreatedAt(auditLog.getCreatedAt() == null ? LocalDateTime.now() : auditLog.getCreatedAt());
        return entity;
    }

    private AuditLog toDomain(AuditLogJpaEntity entity) {
        return AuditLog.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .actorId(entity.getActorId())
                .actorName(entity.getActorName())
                .action(entity.getAction())
                .actionLabel(entity.getActionLabel())
                .targetType(entity.getTargetType())
                .targetId(entity.getTargetId())
                .detail(entity.getDetail())
                .result(entity.getResult())
                .errorMessage(entity.getErrorMessage())
                .ip(entity.getIp())
                .traceId(entity.getTraceId())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
