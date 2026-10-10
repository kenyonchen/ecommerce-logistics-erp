package site.hexaarch.ecommerce.logistics.domain.tenant.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import site.hexaarch.ecommerce.logistics.domain.common.BaseEntity;
import site.hexaarch.ecommerce.logistics.domain.tenant.valueobject.UserStatus;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 用户实体
 *
 * @author kenyon
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class User extends BaseEntity {
    private String id;
    private String tenantId;
    private String username;
    @JsonIgnore
    private String password;
    private String email;
    private String phone;
    private String firstName;
    private String lastName;
    private UserStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 用户关联的角色
    private final Set<Role> roles = new HashSet<>();

    // 私有构造函数，通过工厂方法创建
    private User() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = UserStatus.ACTIVE;
    }

    private User(LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.status = UserStatus.ACTIVE;
    }

    /**
     * 从持久化数据重建用户：落库时间必须由入参带入。
     * <p>
     * 不能用 {@link #create} 重建——{@code createdAt} 是 final 且只在构造里赋 {@code now()}，
     * 走 create() 会让「读回来的创建时间」变成「本次请求的时刻」，
     * 并让随后的 {@code save()} 把库里的 {@code created_at} 覆盖成当前时间（真实数据丢失）。
     * </p>
     */
    public static User restore(String id, String tenantId, String username, String password, String email,
                               String phone, String firstName, String lastName,
                               UserStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        User user = new User(createdAt, updatedAt);
        user.id = id;
        user.tenantId = tenantId;
        user.username = username;
        user.password = password;
        user.email = email;
        user.phone = phone;
        user.firstName = firstName;
        user.lastName = lastName;
        user.status = status == null ? UserStatus.ACTIVE : status;
        return user;
    }

    // 工厂方法
    public static User create(String tenantId, String username, String password, String email,
                              String phone, String firstName, String lastName) {
        User user = new User();
        user.id = UUID.randomUUID().toString();  // 生成唯一ID
        user.tenantId = tenantId;
        user.username = username;
        user.password = password;
        user.email = email;
        user.phone = phone;
        user.firstName = firstName;
        user.lastName = lastName;
        return user;
    }

    // 激活用户
    public void activate() {
        this.status = UserStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    // 禁用用户
    public void deactivate() {
        this.status = UserStatus.INACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    // 锁定用户
    public void lock() {
        this.status = UserStatus.LOCKED;
        this.updatedAt = LocalDateTime.now();
    }

    // 更新用户信息
    public void updateInfo(String email, String phone, String firstName, String lastName) {
        this.email = email;
        this.phone = phone;
        this.firstName = firstName;
        this.lastName = lastName;
        this.updatedAt = LocalDateTime.now();
    }

    // 更新密码
    public void updatePassword(String password) {
        this.password = password;
        this.updatedAt = LocalDateTime.now();
    }

    // 添加角色（按角色ID判重，重复授予同一角色不会产生重复绑定）
    public void addRole(Role role) {
        if (role == null || role.getId() == null) {
            return;
        }
        this.roles.removeIf(existing -> sameRoleId(existing, role));
        this.roles.add(role);
    }

    /**
     * 移除角色（按角色ID匹配）。
     * <p>
     * 这里不能用 {@code Set.remove(Object)}：{@code Role} 的 equals 由 Lombok {@code @Data}
     * 生成、比较的是全部字段，而 {@code createdAt} 在每次构造时都是新的 {@code LocalDateTime.now()}，
     * 同一个角色从不同路径加载出的两个实例 equals 恒为 false，
     * 会导致「撤销角色」接口返回 200 但实际没撤掉。
     * </p>
     */
    public void removeRole(Role role) {
        if (role == null || role.getId() == null) {
            return;
        }
        this.roles.removeIf(existing -> sameRoleId(existing, role));
    }

    private static boolean sameRoleId(Role left, Role right) {
        return left != null && left.getId() != null && left.getId().equals(right.getId());
    }
}