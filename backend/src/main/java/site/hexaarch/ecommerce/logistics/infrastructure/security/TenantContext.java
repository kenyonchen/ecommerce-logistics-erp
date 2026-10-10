package site.hexaarch.ecommerce.logistics.infrastructure.security;

/**
 * 租户上下文
 *
 * @author kenyon
 */
public class TenantContext {

    /**
     * 历史默认桶的字面量。它<b>不是</b>一个可寻址的租户（{@code tenants} 表里没有这一行），
     * 只允许作为"完全没有会话"时的最后兜底值存在，见 {@code TenantWriteFallbackGuardTest}。
     */
    public static final String LEGACY_TENANT_ID = "default-tenant";

    private static final ThreadLocal<String> tenantIdHolder = new ThreadLocal<>();

    /**
     * 把"没传"与"传了历史默认桶字面量"归成一类：两者都表示「由会话决定归属」。
     * <p>
     * 这条规则必须在<b>能看到原始入参的最外层</b>生效。前端曾有模块级常量
     * {@code localStorage.getItem('tenantId') || 'default-tenant'}（登录前就求值，整个会话都带着它发请求），
     * 按字面处理有两种坏结果：租户用户被 {@code TENANT_ACCESS_DENIED} 判成越权（实测 400，登录一次就用不了），
     * 平台管理员则真的把数据写进没有租户行的幻影桶。两者都不是调用方的意图。
     * </p>
     *
     * @param requestedTenantId 调用方传入的租户ID，可为空
     * @return 去除首尾空白后的租户ID；未传或传的是历史默认桶字面量时返回 {@code null}
     */
    public static String normalizeRequested(String requestedTenantId) {
        if (requestedTenantId == null || requestedTenantId.isBlank()) {
            return null;
        }
        String trimmed = requestedTenantId.trim();
        return LEGACY_TENANT_ID.equals(trimmed) ? null : trimmed;
    }

    /**
     * 获取租户ID
     *
     * @return 租户ID
     */
    public static String getTenantId() {
        return tenantIdHolder.get();
    }

    /**
     * 设置租户ID
     *
     * @param tenantId 租户ID
     */
    public static void setTenantId(String tenantId) {
        tenantIdHolder.set(tenantId);
    }

    /**
     * 清除租户ID
     */
    public static void clearTenantId() {
        tenantIdHolder.remove();
    }

    /**
     * 检查是否有租户ID
     *
     * @return 是否有租户ID
     */
    public static boolean hasTenantId() {
        return tenantIdHolder.get() != null;
    }
}