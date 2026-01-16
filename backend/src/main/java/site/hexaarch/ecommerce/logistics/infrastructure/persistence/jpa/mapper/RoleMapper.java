package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.Role;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.RoleJpaEntity;

import java.util.Set;

/**
 * 角色映射器，用于在角色领域模型和JPA实体之间进行转换。
 *
 * @author kenyon
 */
@Mapper(componentModel = "spring")
public interface RoleMapper {

    /**
     * 将角色领域模型转换为JPA实体
     */
    @Mapping(source = "id", target = "id")
    @Mapping(source = "tenantId", target = "tenantId")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "systemRole", target = "systemRole")
    @Mapping(source = "permissions", target = "permissions")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "updatedAt", target = "updatedAt")
    RoleJpaEntity toJpaEntity(Role role);

    /**
     * 将JPA实体转换为角色领域模型
     */
    default Role toDomainEntity(RoleJpaEntity roleJpaEntity) {
        if (roleJpaEntity == null) {
            return null;
        }

        // 使用Role类的工厂方法创建Role实例
        Role role;
        if (roleJpaEntity.isSystemRole()) {
            role = Role.createSystemRole(
                    roleJpaEntity.getName(),
                    roleJpaEntity.getDescription()
            );
        } else {
            role = Role.create(
                    roleJpaEntity.getTenantId(),
                    roleJpaEntity.getName(),
                    roleJpaEntity.getDescription()
            );
        }

        // 设置其他属性
        role.setId(roleJpaEntity.getId());

        // 设置权限
        Set<String> permissions = roleJpaEntity.getPermissions();
        if (permissions != null) {
            role.setPermissions(permissions);
        }

        return role;
    }

}
