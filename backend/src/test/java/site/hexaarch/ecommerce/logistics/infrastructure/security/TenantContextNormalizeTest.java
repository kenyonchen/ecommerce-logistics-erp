package site.hexaarch.ecommerce.logistics.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 租户入参归一化测试（ISS-120 / R-32）。
 * <p>
 * "传历史默认桶字面量" 与 "没传" 必须是同一个语义：由会话决定归属。这条规则住在 {@link TenantContext}
 * 是因为它有两个调用方——{@code TenantAccessGuard}（应用层入口）和 {@code TenantIsolationAspect}
 * （{@code @Before} 切面，跑在服务体之前）。两边口径不一致时，客户端带字面量的写请求会先被切面判成
 * 跨租户越权（实测 400），归一化根本轮不到。
 * </p>
 *
 * @author kenyon
 */
class TenantContextNormalizeTest {

    @Test
    @DisplayName("未传、空白、历史默认桶字面量都归一为「由会话决定」")
    void absentBlankAndLegacyLiteralAllMeanUnspecified() {
        assertNull(TenantContext.normalizeRequested(null));
        assertNull(TenantContext.normalizeRequested(""));
        assertNull(TenantContext.normalizeRequested("   "));
        assertNull(TenantContext.normalizeRequested(TenantContext.LEGACY_TENANT_ID));
        assertNull(TenantContext.normalizeRequested("  " + TenantContext.LEGACY_TENANT_ID + "  "));
    }

    @Test
    @DisplayName("真实租户ID只做裁剪，大小写与前后空格不改写归属")
    void realTenantIdIsOnlyTrimmed() {
        assertEquals("d0861d6f-7d51-4ec5-a733-fda0a0058176",
                TenantContext.normalizeRequested(" d0861d6f-7d51-4ec5-a733-fda0a0058176 "));
        // 幻影桶字面量只在完全相等时归一：近似值仍是"显式指定了一个租户"，交给守卫判越权
        assertEquals("Default-tenant", TenantContext.normalizeRequested("Default-tenant"));
        assertEquals("default-tenant-2", TenantContext.normalizeRequested("default-tenant-2"));
        assertEquals("default", TenantContext.normalizeRequested("default"));
    }

    @Test
    @DisplayName("常量字面量与历史数据保持一致")
    void legacyConstantMatchesHistoricalRows() {
        assertEquals("default-tenant", TenantContext.LEGACY_TENANT_ID);
    }
}
