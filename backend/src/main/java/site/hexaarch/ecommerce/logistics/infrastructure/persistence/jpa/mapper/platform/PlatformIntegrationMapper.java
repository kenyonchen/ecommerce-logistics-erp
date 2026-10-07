package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.platform;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import site.hexaarch.ecommerce.logistics.domain.platform.aggregate.PlatformIntegration;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformName;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.platform.PlatformAuthTypeJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.platform.PlatformIntegrationJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.platform.PlatformNameJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.platform.PlatformStatusJpaEntity;

/**
 * 平台集成映射器
 *
 * @author kenyon
 */
@Mapper(componentModel = "spring")
public interface PlatformIntegrationMapper {

    @Mapping(source = "platformName", target = "platformName", qualifiedByName = "platformNameToJpaEntity")
    @Mapping(source = "status", target = "status", qualifiedByName = "platformStatusToJpaEntity")
    @Mapping(source = "authType", target = "authType", qualifiedByName = "authTypeToJpaEntity")
    PlatformIntegrationJpaEntity toJpaEntity(PlatformIntegration platformIntegration);

    @Mapping(source = "platformName", target = "platformName", qualifiedByName = "jpaEntityToPlatformName")
    @Mapping(source = "status", target = "status", qualifiedByName = "jpaEntityToPlatformStatus")
    @Mapping(source = "authType", target = "authType", qualifiedByName = "jpaEntityToAuthType")
    PlatformIntegration toDomain(PlatformIntegrationJpaEntity jpaEntity);

    @Named("authTypeToJpaEntity")
    default PlatformAuthTypeJpaEntity authTypeToJpaEntity(PlatformAuthType authType) {
        if (authType == null) {
            return null;
        }
        return PlatformAuthTypeJpaEntity.valueOf(authType.name());
    }

    @Named("jpaEntityToAuthType")
    default PlatformAuthType jpaEntityToAuthType(PlatformAuthTypeJpaEntity jpaEntity) {
        if (jpaEntity == null) {
            return null;
        }
        return PlatformAuthType.valueOf(jpaEntity.name());
    }

    @Named("platformNameToJpaEntity")
    default PlatformNameJpaEntity platformNameToJpaEntity(PlatformName platformName) {
        if (platformName == null) {
            return null;
        }
        return PlatformNameJpaEntity.valueOf(platformName.name());
    }

    @Named("jpaEntityToPlatformName")
    default PlatformName jpaEntityToPlatformName(PlatformNameJpaEntity jpaEntity) {
        if (jpaEntity == null) {
            return null;
        }
        return PlatformName.valueOf(jpaEntity.name());
    }

    @Named("platformStatusToJpaEntity")
    default PlatformStatusJpaEntity platformStatusToJpaEntity(PlatformStatus status) {
        if (status == null) {
            return null;
        }
        return PlatformStatusJpaEntity.valueOf(status.name());
    }

    @Named("jpaEntityToPlatformStatus")
    default PlatformStatus jpaEntityToPlatformStatus(PlatformStatusJpaEntity jpaEntity) {
        if (jpaEntity == null) {
            return null;
        }
        return PlatformStatus.valueOf(jpaEntity.name());
    }
}
