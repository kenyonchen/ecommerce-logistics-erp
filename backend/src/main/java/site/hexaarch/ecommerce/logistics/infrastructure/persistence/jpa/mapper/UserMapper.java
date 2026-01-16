package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.User;
import site.hexaarch.ecommerce.logistics.domain.tenant.valueobject.UserStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.UserJpaEntity;

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
    UserJpaEntity toJpaEntity(User user);

    /**
     * 将JPA实体转换为用户领域模型
     */
    default User toDomainEntity(UserJpaEntity userJpaEntity) {
        if (userJpaEntity == null) {
            return null;
        }

        // 使用User类的工厂方法创建User实例
        User user = User.create(
                userJpaEntity.getTenantId(),
                userJpaEntity.getUsername(),
                userJpaEntity.getPassword(),
                userJpaEntity.getEmail(),
                userJpaEntity.getPhone(),
                userJpaEntity.getFirstName(),
                userJpaEntity.getLastName()
        );

        // 设置其他属性
        user.setId(userJpaEntity.getId());

        // 设置状态
        try {
            UserStatus status = UserStatus.valueOf(userJpaEntity.getStatus());
            switch (status) {
                case ACTIVE:
                    user.activate();
                    break;
                case INACTIVE:
                    user.deactivate();
                    break;
                case LOCKED:
                    user.lock();
                    break;
            }
        } catch (IllegalArgumentException e) {
            // 默认状态为ACTIVE
            user.activate();
        }

        return user;
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
