package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.tenant;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.Role;

import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.tenant.RoleJpaEntity;


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
     * <p>
     * 重建必须走 {@link Role#restore}：create() 会把 createdAt 写成 now()，
     * 既让读接口返回"本次请求的时刻"，又让下一次 save() 把库里的 created_at 覆盖掉（ISS-125）。
     * </p>
     */
    default Role toDomainEntity(RoleJpaEntity roleJpaEntity) {
        if (roleJpaEntity == null) {
            return null;
        }

        // 权限必须拷贝成普通集合：把 Hibernate 的 PersistentSet 交给领域对象，
        // 会话关闭后读取它会抛 LazyInitializationException，领域侧的改动也会绕过脏检查写回库表
        return Role.restore(
                roleJpaEntity.getId(),
                roleJpaEntity.getTenantId(),
                roleJpaEntity.getName(),
                roleJpaEntity.getDescription(),
                roleJpaEntity.isSystemRole(),
                roleJpaEntity.getPermissions(),
                roleJpaEntity.getCreatedAt(),
                roleJpaEntity.getUpdatedAt()
        );
    }

}
