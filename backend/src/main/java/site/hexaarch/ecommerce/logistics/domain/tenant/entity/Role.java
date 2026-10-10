package site.hexaarch.ecommerce.logistics.domain.tenant.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import site.hexaarch.ecommerce.logistics.domain.common.BaseEntity;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.tenant.RoleNames;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

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

    /**
     * 角色名规则：中文/字母/数字/下划线/连字符，1-64位。
     * 角色名会以 ROLE_&lt;name&gt; 形式进入 Spring Security 上下文，禁止空格与特殊字符。
     */
    private static final Pattern ROLE_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_\\u4e00-\\u9fa5-]{1,64}$");

    /**
     * 权限点规则：模块:动作，模块为小写字母开头的小写串，动作为小写字母/数字/下划线/连字符/通配符。
     * 例：order:read、product:import、platform:manage、warehouse:*
     */
    private static final Pattern PERMISSION_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*:[a-z0-9_*-]+$");

    // 私有构造函数，通过工厂方法创建
    private Role() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.isSystemRole = false;
    }

    private Role(LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.isSystemRole = false;
    }

    /**
     * 从持久化数据重建角色：落库时间必须由入参带入。
     * <p>
     * 不能用 {@link #create} 重建——{@code createdAt} 是 final 且只在构造里赋 {@code now()}，
     * 走 create() 会让「读回来的创建时间」变成「本次请求的时刻」，
     * 并让随后的 {@code save()} 把库里的 {@code created_at} 覆盖成当前时间（真实数据丢失）。
     * </p>
     */
    public static Role restore(String id, String tenantId, String name, String description,
                               boolean systemRole, Set<String> permissions,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {
        Role.validateName(name);
        Role role = new Role(createdAt, updatedAt);
        role.id = id;
        role.tenantId = tenantId;
        role.name = name;
        role.description = description;
        role.isSystemRole = systemRole;
        role.permissions = permissions == null ? new HashSet<>() : new HashSet<>(permissions);
        return role;
    }

    // 工厂方法
    public static Role create(String tenantId, String name, String description) {
        validateName(name);
        Role role = new Role();
        role.id = UUID.randomUUID().toString();  // 生成唯一ID
        role.tenantId = tenantId;
        role.name = name;
        role.description = description;
        return role;
    }

    // 创建系统角色
    public static Role createSystemRole(String name, String description) {
        validateName(name);
        Role role = new Role();
        role.id = UUID.randomUUID().toString();  // 生成唯一ID
        role.tenantId = RoleNames.SYSTEM_TENANT_ID;
        role.name = name;
        role.description = description;
        role.isSystemRole = true;
        return role;
    }

    // 更新角色信息
    public void updateInfo(String name, String description) {
        validateName(name);
        this.name = name;
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }

    // 添加权限（入口校验）
    public void addPermission(String permission) {
        validatePermission(permission);
        this.permissions.add(permission);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 整组重置权限（逐条走 addPermission 校验）
     */
    public void resetPermissions(Iterable<String> newPermissions) {
        this.permissions.clear();
        if (newPermissions != null) {
            for (String permission : newPermissions) {
                addPermission(permission);
            }
        }
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 校验角色名：供角色名变更入口复用
     */
    public static void validateName(String name) {
        if (name == null || !ROLE_NAME_PATTERN.matcher(name).matches()) {
            throw new BusinessException(
                    "角色名称不合法：仅允许中文、字母、数字、下划线、连字符，长度1-64", "ROLE_NAME_INVALID");
        }
    }

    /**
     * 校验权限点格式：模块:动作（小写）
     */
    public static void validatePermission(String permission) {
        if (permission == null || !PERMISSION_PATTERN.matcher(permission.trim().toLowerCase(Locale.ROOT)).matches()) {
            throw new BusinessException(
                    "权限点不合法：需为小写的 模块:动作 格式（如 order:read、platform:manage）", "PERMISSION_INVALID");
        }
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