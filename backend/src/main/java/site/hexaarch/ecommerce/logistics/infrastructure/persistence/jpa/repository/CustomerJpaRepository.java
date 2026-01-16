package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.CustomerJpaEntity;

import java.util.UUID;

/**
 * 客户JPA仓库接口
 *
 * @author kenyon
 */
public interface CustomerJpaRepository extends JpaRepository<CustomerJpaEntity, UUID> {
}
