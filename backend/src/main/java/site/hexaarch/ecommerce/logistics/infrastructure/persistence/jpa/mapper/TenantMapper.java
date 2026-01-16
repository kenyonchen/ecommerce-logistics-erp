package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import site.hexaarch.ecommerce.logistics.domain.tenant.aggregate.Tenant;
import site.hexaarch.ecommerce.logistics.domain.tenant.valueobject.TenantStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.TenantJpaEntity;

/**
 * 租户映射器，用于在租户领域模型和JPA实体之间进行转换。
 *
 * @author kenyon
 */
@Mapper(componentModel = "spring")
public interface TenantMapper {

    /**
     * 将租户领域模型转换为JPA实体
     */
    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "contactEmail", target = "contactEmail")
    @Mapping(source = "contactPhone", target = "contactPhone")
    @Mapping(source = "status", target = "status", qualifiedByName = "tenantStatusToString")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "updatedAt", target = "updatedAt")
    TenantJpaEntity toJpaEntity(Tenant tenant);

    /**
     * 将JPA实体转换为租户领域模型
     */
    default Tenant toDomainEntity(TenantJpaEntity tenantJpaEntity) {
        if (tenantJpaEntity == null) {
            return null;
        }

        // 使用Tenant类的工厂方法创建Tenant实例
        Tenant tenant = Tenant.create(
                tenantJpaEntity.getName(),
                tenantJpaEntity.getDescription(),
                tenantJpaEntity.getContactEmail(),
                tenantJpaEntity.getContactPhone()
        );

        // 设置其他属性
        tenant.setId(tenantJpaEntity.getId());

        // 设置状态
        try {
            TenantStatus status = TenantStatus.valueOf(tenantJpaEntity.getStatus());
            switch (status) {
                case ACTIVE:
                    tenant.activate();
                    break;
                case SUSPENDED:
                    tenant.suspend();
                    break;
                case INACTIVE:
                    tenant.deactivate();
                    break;
            }
        } catch (IllegalArgumentException e) {
            // 默认状态为ACTIVE
            tenant.activate();
        }

        return tenant;
    }

    /**
     * 将TenantStatus转换为String
     */
    @Named("tenantStatusToString")
    default String tenantStatusToString(TenantStatus status) {
        return status != null ? status.name() : TenantStatus.ACTIVE.name();
    }

}
