package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.tenant;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.Role;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.User;
import site.hexaarch.ecommerce.logistics.domain.tenant.valueobject.UserStatus;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.tenant.UserJpaEntity;

/**
 * 用户映射器，用于在用户领域模型和JPA实体之间进行转换。
 *
 * @author kenyon
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    /**
     * 将用户领域模型转换为JPA实体
     */
    @Mapping(source = "id", target = "id")
    @Mapping(source = "tenantId", target = "tenantId")
    @Mapping(source = "username", target = "username")
    @Mapping(source = "password", target = "password")
    @Mapping(source = "email", target = "email")
    @Mapping(source = "phone", target = "phone")
    @Mapping(source = "firstName", target = "firstName")
    @Mapping(source = "lastName", target = "lastName")
    @Mapping(source = "status", target = "status", qualifiedByName = "userStatusToString")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "updatedAt", target = "updatedAt")
    @Mapping(source = "roles", target = "roleIds", qualifiedByName = "rolesToRoleIds")
    UserJpaEntity toJpaEntity(User user);

    /**
     * 将JPA实体转换为用户领域模型
     * <p>
     * 重建必须走 {@link User#restore}：create() 会把 createdAt 写成 now()，
     * 既让读接口返回"本次请求的时刻"，又让下一次 save() 把库里的 created_at 覆盖掉（ISS-125）。
     * </p>
     */
    default User toDomainEntity(UserJpaEntity userJpaEntity) {
        if (userJpaEntity == null) {
            return null;
        }

        return User.restore(
                userJpaEntity.getId(),
                userJpaEntity.getTenantId(),
                userJpaEntity.getUsername(),
                userJpaEntity.getPassword(),
                userJpaEntity.getEmail(),
                userJpaEntity.getPhone(),
                userJpaEntity.getFirstName(),
                userJpaEntity.getLastName(),
                stringToUserStatus(userJpaEntity.getStatus()),
                userJpaEntity.getCreatedAt(),
                userJpaEntity.getUpdatedAt()
        );
    }

    /**
     * 将角色集合转换为角色ID集合
     */
    @Named("rolesToRoleIds")
    default Set<String> rolesToRoleIds(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return new HashSet<>();
        }
        return roles.stream()
                .map(Role::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * 将UserStatus转换为String
     */
    @Named("userStatusToString")
    default String userStatusToString(UserStatus status) {
        return status != null ? status.name() : UserStatus.ACTIVE.name();
    }

    /**
     * 将String转换为UserStatus
     */
    @Named("stringToUserStatus")
    default UserStatus stringToUserStatus(String status) {
        try {
            return UserStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return UserStatus.ACTIVE;
        }
    }
}
