package site.hexaarch.ecommerce.logistics.domain.tenant.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import site.hexaarch.ecommerce.logistics.domain.common.BaseEntity;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 角色实体
 *
 * @author kenyon
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Role extends BaseEntity {
    private String id;
    private String tenantId;
    private String name;
    private String description;
    private boolean isSystemRole;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 角色关联的权限
    private Set<String> permissions = new HashSet<>();

    // 私有构造函数，通过工厂方法创建
    private Role() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.isSystemRole = false;
    }

    // 工厂方法
    public static Role create(String tenantId, String name, String description) {
        Role role = new Role();
        role.id = UUID.randomUUID().toString();  // 生成唯一ID
        role.tenantId = tenantId;
        role.name = name;
        role.description = description;
        return role;
    }

    // 创建系统角色
    public static Role createSystemRole(String name, String description) {
        Role role = new Role();
        role.id = UUID.randomUUID().toString();  // 生成唯一ID
        role.tenantId = "SYSTEM";
        role.name = name;
        role.description = description;
        role.isSystemRole = true;
        return role;
    }

    // 更新角色信息
    public void updateInfo(String name, String description) {
        this.name = name;
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }

    // 添加权限
    public void addPermission(String permission) {
        this.permissions.add(permission);
        this.updatedAt = LocalDateTime.now();
    }

    // 移除权限
    public void removePermission(String permission) {
        this.permissions.remove(permission);
        this.updatedAt = LocalDateTime.now();
    }

    // 检查是否拥有权限
    public boolean hasPermission(String permission) {
        return this.permissions.contains(permission);
    }
}