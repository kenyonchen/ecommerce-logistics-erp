package site.hexaarch.ecommerce.logistics.infrastructure.security;

/**
 * 租户上下文
 *
 * @author kenyon
 */
public class TenantContext {

    private static final ThreadLocal<String> tenantIdHolder = new ThreadLocal<>();

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