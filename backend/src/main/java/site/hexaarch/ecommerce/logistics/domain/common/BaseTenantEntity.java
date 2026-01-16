package site.hexaarch.ecommerce.logistics.domain.common;

import lombok.Getter;
import lombok.Setter;

/**
 * 基础租户实体
 * 所有需要租户隔离的实体都应该继承此类
 *
 * @author kenyon
 */
public abstract class BaseTenantEntity {

    @Getter
    @Setter
    private String tenantId;
}