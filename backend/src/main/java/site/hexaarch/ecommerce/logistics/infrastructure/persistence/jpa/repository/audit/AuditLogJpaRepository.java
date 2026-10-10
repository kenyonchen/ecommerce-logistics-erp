package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.domain.audit.valueobject.AuditResult;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.audit.AuditLogJpaEntity;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * 审计日志JPA仓库接口。分页/最近查询走 {@link JpaSpecificationExecutor} 上的条件拼装。
 *
 * @author kenyon
 */
@Repository
public interface AuditLogJpaRepository extends JpaRepository<AuditLogJpaEntity, String>,
        JpaSpecificationExecutor<AuditLogJpaEntity> {

    long countByActionInAndActorNameAndResultAndCreatedAtAfter(
            Collection<String> actions, String actorName, AuditResult result, LocalDateTime since);
}
