package site.hexaarch.ecommerce.logistics.application.service.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.tenant.aggregate.Tenant;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.TenantRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.security.TenantContext;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 读/写路径租户守卫测试（ISS-050）。
 *
 * @author kenyon
 */
class TenantAccessGuardTest {

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        TenantContext.clearTenantId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clearTenantId();
    }

    private void login(String tenantId, String... roles) {
        TenantContext.setTenantId(tenantId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user", null, AuthorityUtils.createAuthorityList(roles)));
    }

    @Test
    void 租户用户按ID直读他租户聚合应抛NotFound() {
        login(TENANT_B, "ROLE_TENANT_ADMIN");

        assertThrows(EntityNotFoundException.class,
                () -> TenantAccessGuard.requireOwned("Order", TENANT_A, "order-1"));
    }

    @Test
    void 租户用户读本租户聚合放行() {
        login(TENANT_B, "ROLE_TENANT_ADMIN");

        TenantAccessGuard.requireOwned("Order", TENANT_B, "order-1");
    }

    @Test
    void 平台管理员跨租户读取保持放行() {
        login(TENANT_A, "ROLE_PLATFORM_ADMIN", "ROLE_TENANT_ADMIN");

        TenantAccessGuard.requireOwned("Order", TENANT_B, "order-1");
        assertNull(TenantAccessGuard.currentTenantId());
    }

    @Test
    void 缺省租户的列表查询收敛到登录租户() {
        login(TENANT_B, "ROLE_TENANT_ADMIN");

        assertEquals(TENANT_B, TenantAccessGuard.scopedTenantId(null));
        assertEquals(TENANT_B, TenantAccessGuard.scopedTenantId(""));
        assertEquals(TENANT_B, TenantAccessGuard.scopedTenantId(TENANT_B));
    }

    @Test
    void 显式请求他租户的列表查询被拒() {
        login(TENANT_B, "ROLE_TENANT_ADMIN");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> TenantAccessGuard.scopedTenantId(TENANT_A));
        assertEquals("TENANT_ACCESS_DENIED", ex.getErrorCode());
    }

    @Test
    void 无租户上下文时不做过滤以免误伤内部调用() {
        assertNull(TenantAccessGuard.currentTenantId());
        assertNull(TenantAccessGuard.scopedTenantId(null));
        TenantAccessGuard.requireOwned("Order", TENANT_A, "order-1");
    }

    @Test
    void 写入未传租户时归属登录租户而非历史默认桶() {
        login(TENANT_B, "ROLE_TENANT_ADMIN");

        // 导入落 default-tenant、列表按登录租户收敛 ⇒ 导入成功的行没人看得见（冒烟 6.2c）
        assertEquals(TENANT_B, TenantAccessGuard.resolveWriteTenant(null, "default-tenant"));
        assertEquals(TENANT_B, TenantAccessGuard.resolveWriteTenant("  ", "default-tenant"));
    }

    @Test
    void 平台管理员写入也归属自己的主租户() {
        login(TENANT_A, "ROLE_PLATFORM_ADMIN", "ROLE_TENANT_ADMIN");

        assertEquals(TENANT_A, TenantAccessGuard.resolveWriteTenant(null, "default-tenant"));
        // 显式指定他租户时，平台管理员保持放行（代客操作）
        assertEquals(TENANT_B, TenantAccessGuard.resolveWriteTenant(TENANT_B, "default-tenant"));
    }

    @Test
    void 写入显式传他租户被拒而完全无上下文回退默认桶() {
        login(TENANT_B, "ROLE_TENANT_ADMIN");

        assertThrows(BusinessException.class,
                () -> TenantAccessGuard.resolveWriteTenant(TENANT_A, "default-tenant"));

        SecurityContextHolder.clearContext();
        TenantContext.clearTenantId();
        assertEquals("default-tenant", TenantAccessGuard.resolveWriteTenant(null, "default-tenant"));
    }

    /**
     * 前端的模块级常量 {@code localStorage.getItem('tenantId') || 'default-tenant'} 在登录前就求值，
     * 于是整个会话都带着 {@code default-tenant} 发请求。按字面处理有两种坏结果（都实测过）：
     * 租户用户的读与写被 400 {@code TENANT_ACCESS_DENIED} 判成越权，平台管理员则真的把数据写进幻影桶。
     */
    @Test
    void 客户端把历史默认桶当显式租户传上来时按未提供处理() {
        login(TENANT_B, "ROLE_TENANT_ADMIN");

        assertEquals(TENANT_B, TenantAccessGuard.scopedTenantId("default-tenant"));
        assertEquals(TENANT_B, TenantAccessGuard.scopedTenantId(" default-tenant "));
        assertEquals(TENANT_B, TenantAccessGuard.resolveWriteTenant("default-tenant", "default-tenant"));
    }

    @Test
    void 平台管理员同样无法寻址历史默认桶() {
        login(TENANT_A, "ROLE_PLATFORM_ADMIN", "ROLE_TENANT_ADMIN");

        // 读：归一化成"不按租户过滤"，而不是过滤到一个没有租户行的桶
        assertNull(TenantAccessGuard.scopedTenantId(TenantAccessGuard.LEGACY_TENANT_ID));
        // 写：落到自己的主租户，不落幻影桶
        assertEquals(TENANT_A, TenantAccessGuard.resolveWriteTenant(
                TenantAccessGuard.LEGACY_TENANT_ID, TenantAccessGuard.LEGACY_TENANT_ID));
    }

    /**
     * ISS-135 的实测缺口：平台管理员「代客写」只看是不是平台角色，不看被点名的租户在不在。
     * {@code tenant_id} 是普通列而非指向 {@code tenants} 的外键（见 {@code TenantRepository#countBusinessRecords}
     * 的说明），所以数据库也不会挡 —— 行落到一个没有租户的归属上，对任何用户都不可见，
     * 而 §13 的幻影巡检只查 {@code default-tenant / NULL / 空串}，抓不到任意错写的 ID。
     */
    @Test
    void 客户端显式指定不存在的租户时拒绝归属() {
        TenantRepository tenantRepository = mock(TenantRepository.class);
        when(tenantRepository.findById(TENANT_B)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> TenantAccessGuard.requireExistingTenant(TENANT_B, tenantRepository));

        assertEquals("TENANT_NOT_FOUND", exception.getErrorCode());
        assertTrue(exception.getMessage().contains(TENANT_B), exception.getMessage());
    }

    /**
     * 负例：真实存在的租户必须照常放行——否则「代客写」这条合法能力会被存在性校验一起关掉，
     * 那条豁免本身就是平台管理员的日常用法（{@code 平台管理员写入也归属自己的主租户} 另一半）。
     */
    @Test
    void 租户真实存在时存在性校验放行() {
        TenantRepository tenantRepository = mock(TenantRepository.class);
        when(tenantRepository.findById(TENANT_B)).thenReturn(Optional.of(mock(Tenant.class)));

        TenantAccessGuard.requireExistingTenant(TENANT_B, tenantRepository);

        verify(tenantRepository).findById(TENANT_B);
    }

    /**
     * 未指定与历史默认桶都不参与存在性判定：{@code default-tenant} 在 {@code tenants} 里本来就没有行，
     * 按存在性判红会把 ISS-118 定下的「兜底桶」重新变成报错；兜底桶由 §13 幻影巡检负责，不由请求校验负责。
     */
    @Test
    void 未指定与历史默认桶不查库也不报错() {
        TenantRepository tenantRepository = mock(TenantRepository.class);

        TenantAccessGuard.requireExistingTenant(null, tenantRepository);
        TenantAccessGuard.requireExistingTenant("   ", tenantRepository);
        TenantAccessGuard.requireExistingTenant(TenantAccessGuard.LEGACY_TENANT_ID, tenantRepository);

        verifyNoInteractions(tenantRepository);
    }
}
