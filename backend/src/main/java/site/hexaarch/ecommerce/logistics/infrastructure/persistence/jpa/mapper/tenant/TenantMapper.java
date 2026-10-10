package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.tenant;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import site.hexaarch.ecommerce.logistics.domain.tenant.aggregate.Tenant;
import site.hexaarch.ecommerce.logistics.domain.tenant.valueobject.TenantStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.tenant.TenantJpaEntity;

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
     * <p>
     * 重建必须走 {@link Tenant#restore}：create() 会把 createdAt 写成 now()，
     * 既让读接口返回"本次请求的时刻"，又让下一次 save() 把库里的 created_at 覆盖掉（ISS-125）。
     * </p>
     */
    default Tenant toDomainEntity(TenantJpaEntity tenantJpaEntity) {
        if (tenantJpaEntity == null) {
            return null;
        }

        return Tenant.restore(
                tenantJpaEntity.getId(),
                tenantJpaEntity.getName(),
                tenantJpaEntity.getDescription(),
                tenantJpaEntity.getContactEmail(),
                tenantJpaEntity.getContactPhone(),
                stringToTenantStatus(tenantJpaEntity.getStatus()),
                tenantJpaEntity.getCreatedAt(),
                tenantJpaEntity.getUpdatedAt()
        );
    }

    /**
     * 将String转换为TenantStatus（非法值按 ACTIVE 处理，与原重建逻辑一致）
     */
    @Named("stringToTenantStatus")
    default TenantStatus stringToTenantStatus(String status) {
        try {
            return TenantStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return TenantStatus.ACTIVE;
        }
    }

    /**
     * 将TenantStatus转换为String
     */
    @Named("tenantStatusToString")
    default String tenantStatusToString(TenantStatus status) {
        return status != null ? status.name() : TenantStatus.ACTIVE.name();
    }

}
