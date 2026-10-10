package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import site.hexaarch.ecommerce.logistics.domain.tenant.aggregate.Tenant;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.Role;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.User;
import site.hexaarch.ecommerce.logistics.domain.tenant.valueobject.TenantStatus;
import site.hexaarch.ecommerce.logistics.domain.tenant.valueobject.UserStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.tenant.RoleJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.tenant.TenantJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.tenant.UserJpaEntity;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * ISS-125：租户域的三张表（users / tenants / roles）读路径重建必须带回落库时间。
 * <p>
 * 曾经这三处的 {@code toDomainEntity} 都用 {@code create()} 工厂重建聚合，而 {@code createdAt} 是
 * final 且只在私有构造里赋 {@code LocalDateTime.now()}，于是读接口返回"本次请求的时刻"，
 * 并且下一次 {@code save()} 会把库里的 {@code created_at} 覆盖成当前时间——不只是显示错，是数据丢失。
 * 这里用固定时间戳做断言：一旦有人把重建路径改回 {@code create()}，四条断言全部转红。
 * </p>
 *
 * @author kenyon
 */
class TenantDomainRestoreTest {

    private static final LocalDateTime PERSISTED = LocalDateTime.of(2020, 1, 2, 3, 4, 5);
    private static final LocalDateTime PERSISTED_UPDATED = LocalDateTime.of(2021, 6, 7, 8, 9, 10);

    private final UserMapper userMapper = Mappers.getMapper(UserMapper.class);
    private final TenantMapper tenantMapper = Mappers.getMapper(TenantMapper.class);
    private final RoleMapper roleMapper = Mappers.getMapper(RoleMapper.class);

    @Test
    @DisplayName("用户：读回带落库时间，写回不覆盖 created_at")
    void userKeepsPersistedTimestamps() {
        UserJpaEntity entity = new UserJpaEntity();
        entity.setId("u-1");
        entity.setTenantId("t-1");
        entity.setUsername("alice");
        entity.setPassword("encoded");
        entity.setEmail("alice@example.com");
        entity.setStatus(UserStatus.LOCKED.name());
        entity.setCreatedAt(PERSISTED);
        entity.setUpdatedAt(PERSISTED_UPDATED);

        User user = userMapper.toDomainEntity(entity);

        assertNotNull(user);
        assertEquals("u-1", user.getId());
        assertEquals(PERSISTED, user.getCreatedAt(), "读回的 createdAt 必须是库里的值，不能是 now()");
        assertEquals(PERSISTED_UPDATED, user.getUpdatedAt());
        assertEquals(UserStatus.LOCKED, user.getStatus(), "状态重建不能再走 activate()/lock() 顺带改 updatedAt");

        assertEquals(PERSISTED, userMapper.toJpaEntity(user).getCreatedAt(),
                "写回时必须把原 created_at 带上，否则 save() 会覆盖库里的创建时间");
    }

    @Test
    @DisplayName("租户：读回带落库时间与状态")
    void tenantKeepsPersistedTimestamps() {
        TenantJpaEntity entity = new TenantJpaEntity();
        entity.setId("t-1");
        entity.setName("测试租户");
        entity.setContactEmail("ops@example.com");
        entity.setStatus(TenantStatus.SUSPENDED.name());
        entity.setCreatedAt(PERSISTED);
        entity.setUpdatedAt(PERSISTED_UPDATED);

        Tenant tenant = tenantMapper.toDomainEntity(entity);

        assertNotNull(tenant);
        assertEquals("t-1", tenant.getId());
        assertEquals(PERSISTED, tenant.getCreatedAt());
        assertEquals(PERSISTED_UPDATED, tenant.getUpdatedAt());
        assertEquals(TenantStatus.SUSPENDED, tenant.getStatus());

        assertEquals(PERSISTED, tenantMapper.toJpaEntity(tenant).getCreatedAt());
    }

    @Test
    @DisplayName("角色：读回带落库时间，系统角色不被改写 tenantId")
    void roleKeepsPersistedTimestamps() {
        RoleJpaEntity entity = new RoleJpaEntity();
        entity.setId("r-1");
        entity.setTenantId("t-1");
        entity.setName("TENANT_ADMIN");
        entity.setDescription("租户管理员");
        entity.setSystemRole(true);
        entity.setPermissions(new HashSet<>(Set.of("user:manage")));
        entity.setCreatedAt(PERSISTED);
        entity.setUpdatedAt(PERSISTED_UPDATED);

        Role role = roleMapper.toDomainEntity(entity);

        assertNotNull(role);
        assertEquals("r-1", role.getId());
        assertEquals(PERSISTED, role.getCreatedAt());
        assertEquals(PERSISTED_UPDATED, role.getUpdatedAt());
        assertEquals("t-1", role.getTenantId(), "系统角色原先走 createSystemRole 会把 tenantId 强写成系统桶");
        org.junit.jupiter.api.Assertions.assertTrue(role.isSystemRole());

        assertEquals(PERSISTED, roleMapper.toJpaEntity(role).getCreatedAt());
    }

    @Test
    @DisplayName("新建路径不变：create() 仍然自己生成时间戳与 id")
    void createStillStampsNow() {
        User user = User.create("t-1", "bob", "encoded", "bob@example.com", null, "Bob", "Li");
        assertNotNull(user.getId());
        assertNotNull(user.getCreatedAt());

        Role role = Role.create("t-1", "ops", "运营");
        assertNotNull(role.getCreatedAt());

        Tenant tenant = Tenant.create("新租户", null, "new@example.com", null);
        assertNotNull(tenant.getCreatedAt());
    }
}
