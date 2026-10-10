package site.hexaarch.ecommerce.logistics.domain.audit;

import java.util.List;

/**
 * 审计动作码的唯一定义处。
 * <p>
 * 动作码会写进 {@code audit_logs.action} 并成为检索条件，因此必须是常量而非散落各控制器的字面量
 * （拼错的审计动作码只会在检索时静默漏记录，比业务代码里的拼错更难发现）。
 * </p>
 *
 * @author kenyon
 */
public final class AuditActions {

    private AuditActions() {
    }

    public static final String AUTH_LOGIN = "AUTH_LOGIN";
    public static final String AUTH_LOGOUT = "AUTH_LOGOUT";
    public static final String AUTH_REFRESH = "AUTH_REFRESH";

    public static final String USER_CREATE = "USER_CREATE";
    public static final String USER_UPDATE = "USER_UPDATE";
    public static final String USER_DELETE = "USER_DELETE";
    public static final String USER_PASSWORD_UPDATE = "USER_PASSWORD_UPDATE";
    public static final String USER_STATUS_UPDATE = "USER_STATUS_UPDATE";
    public static final String USER_ROLE_ASSIGN = "USER_ROLE_ASSIGN";
    public static final String USER_ROLE_REMOVE = "USER_ROLE_REMOVE";

    public static final String ROLE_CREATE = "ROLE_CREATE";
    public static final String ROLE_UPDATE = "ROLE_UPDATE";
    public static final String ROLE_DELETE = "ROLE_DELETE";
    public static final String ROLE_PERMISSION_UPDATE = "ROLE_PERMISSION_UPDATE";

    public static final String TENANT_CREATE = "TENANT_CREATE";
    public static final String TENANT_UPDATE = "TENANT_UPDATE";
    public static final String TENANT_STATUS_UPDATE = "TENANT_STATUS_UPDATE";
    public static final String TENANT_DELETE = "TENANT_DELETE";
    public static final String TENANT_ADMIN_CREATE = "TENANT_ADMIN_CREATE";

    public static final String SHOP_CREATE = "SHOP_CREATE";
    public static final String SHOP_UPDATE = "SHOP_UPDATE";
    public static final String SHOP_DELETE = "SHOP_DELETE";
    public static final String SHOP_PUSHBACK_POLICY_UPDATE = "SHOP_PUSHBACK_POLICY_UPDATE";
    public static final String SHOP_CREDENTIAL_UPDATE = "SHOP_CREDENTIAL_UPDATE";

    public static final String PLATFORM_INTEGRATION_CREATE = "PLATFORM_INTEGRATION_CREATE";
    public static final String PLATFORM_INTEGRATION_UPDATE = "PLATFORM_INTEGRATION_UPDATE";
    public static final String PLATFORM_INTEGRATION_STATUS_UPDATE = "PLATFORM_INTEGRATION_STATUS_UPDATE";
    public static final String PLATFORM_INTEGRATION_DELETE = "PLATFORM_INTEGRATION_DELETE";

    public static final String SYSTEM_CONFIG_UPDATE = "SYSTEM_CONFIG_UPDATE";

    // ------------------------------------------------------------------ 业务单据（金额 / 库存 / 单据状态）
    // security-design §9 第 8 项的口径是「身份/权限/金额/凭据变更一律审计」，
    // 金额与库存的实际变动发生在这几张单据上，所以它们与上面的身份类端点同级。

    public static final String ORDER_CREATE = "ORDER_CREATE";
    public static final String ORDER_STATUS_UPDATE = "ORDER_STATUS_UPDATE";
    public static final String ORDER_CANCEL = "ORDER_CANCEL";
    public static final String ORDER_DELETE = "ORDER_DELETE";

    public static final String FINANCE_TRANSACTION_CREATE = "FINANCE_TRANSACTION_CREATE";
    public static final String FINANCE_TRANSACTION_COMPLETE = "FINANCE_TRANSACTION_COMPLETE";
    public static final String FINANCE_TRANSACTION_FAIL = "FINANCE_TRANSACTION_FAIL";
    public static final String FINANCE_TRANSACTION_CANCEL = "FINANCE_TRANSACTION_CANCEL";

    public static final String PURCHASE_CREATE = "PURCHASE_CREATE";
    public static final String PURCHASE_APPROVE = "PURCHASE_APPROVE";
    public static final String PURCHASE_REJECT = "PURCHASE_REJECT";
    public static final String PURCHASE_TRANSIT = "PURCHASE_TRANSIT";
    public static final String PURCHASE_DELIVERED = "PURCHASE_DELIVERED";
    public static final String PURCHASE_CANCEL = "PURCHASE_CANCEL";
    public static final String PURCHASE_ITEM_ADD = "PURCHASE_ITEM_ADD";
    public static final String PURCHASE_ITEM_UPDATE = "PURCHASE_ITEM_UPDATE";
    public static final String PURCHASE_ITEM_REMOVE = "PURCHASE_ITEM_REMOVE";

    public static final String WAREHOUSE_CREATE = "WAREHOUSE_CREATE";
    public static final String WAREHOUSE_UPDATE = "WAREHOUSE_UPDATE";
    public static final String WAREHOUSE_DELETE = "WAREHOUSE_DELETE";
    public static final String WAREHOUSE_STATUS_UPDATE = "WAREHOUSE_STATUS_UPDATE";
    public static final String WAREHOUSE_LOCATION_ADD = "WAREHOUSE_LOCATION_ADD";

    public static final String INVENTORY_IN = "INVENTORY_IN";
    public static final String INVENTORY_OUT = "INVENTORY_OUT";
    public static final String INVENTORY_COUNT = "INVENTORY_COUNT";
    public static final String INVENTORY_MOVE = "INVENTORY_MOVE";

    public static final String LOGISTICS_CREATE = "LOGISTICS_CREATE";
    public static final String LOGISTICS_STATUS_UPDATE = "LOGISTICS_STATUS_UPDATE";
    public static final String LOGISTICS_SHIP = "LOGISTICS_SHIP";
    public static final String LOGISTICS_BATCH_SHIP = "LOGISTICS_BATCH_SHIP";
    public static final String LOGISTICS_CANCEL = "LOGISTICS_CANCEL";
    public static final String LOGISTICS_LABEL_GENERATE = "LOGISTICS_LABEL_GENERATE";
    public static final String LOGISTICS_TRACK_REFRESH = "LOGISTICS_TRACK_REFRESH";
    public static final String LOGISTICS_TRACE_ADD = "LOGISTICS_TRACE_ADD";
    public static final String LOGISTICS_DELETE = "LOGISTICS_DELETE";

    public static final String LOGISTICS_CHANNEL_CREATE = "LOGISTICS_CHANNEL_CREATE";
    public static final String LOGISTICS_CHANNEL_UPDATE = "LOGISTICS_CHANNEL_UPDATE";
    public static final String LOGISTICS_CHANNEL_STATUS_UPDATE = "LOGISTICS_CHANNEL_STATUS_UPDATE";
    public static final String LOGISTICS_CHANNEL_DELETE = "LOGISTICS_CHANNEL_DELETE";

    // ------------------------------------------------------------------ 主数据（客户为 PII，商品/SKU 是订单与库存的引用源）
    // 这两类不在 §9 第 8 项的字面清单里，但它们的变更同样要能回答「谁在什么时候改的」：
    // 客户资料含姓名/邮箱/电话/地址，商品的 SKU 与重量直接决定运费与报关数据。

    public static final String CUSTOMER_CREATE = "CUSTOMER_CREATE";
    public static final String CUSTOMER_UPDATE = "CUSTOMER_UPDATE";
    public static final String CUSTOMER_STATUS_UPDATE = "CUSTOMER_STATUS_UPDATE";
    public static final String CUSTOMER_DELETE = "CUSTOMER_DELETE";

    public static final String PRODUCT_CREATE = "PRODUCT_CREATE";
    public static final String PRODUCT_UPDATE = "PRODUCT_UPDATE";
    public static final String PRODUCT_STATUS_UPDATE = "PRODUCT_STATUS_UPDATE";
    public static final String PRODUCT_DELETE = "PRODUCT_DELETE";
    public static final String PRODUCT_SKU_ADD = "PRODUCT_SKU_ADD";
    public static final String PRODUCT_SKU_REMOVE = "PRODUCT_SKU_REMOVE";
    public static final String PRODUCT_BATCH_UPDATE = "PRODUCT_BATCH_UPDATE";
    public static final String PRODUCT_BATCH_DELETE = "PRODUCT_BATCH_DELETE";

    public static final String SHOP_WAREHOUSE_BIND = "SHOP_WAREHOUSE_BIND";
    public static final String SHOP_WAREHOUSE_UNBIND = "SHOP_WAREHOUSE_UNBIND";
    public static final String SHOP_WAREHOUSE_SYNC = "SHOP_WAREHOUSE_SYNC";

    // ------------------------------------------------------------------ 平台侧动作
    // 同步与试连会用明文凭据去请求第三方接口，并把外部数据写进本地库；出问题时
    // 「是谁在什么时候用哪条集成拉过数据」是唯一的取证线索。

    public static final String PLATFORM_INTEGRATION_SYNC = "PLATFORM_INTEGRATION_SYNC";
    public static final String PLATFORM_INTEGRATION_TEST = "PLATFORM_INTEGRATION_TEST";
    public static final String PLATFORM_FULFILLMENT_PUSH = "PLATFORM_FULFILLMENT_PUSH";

    // ------------------------------------------------------------------ 批量导入
    // 一次导入可以改写成百上千行，比任何单条写操作更需要留痕；动作码按模块区分，
    // 因为审计检索是按 action 过滤的，混成一个码就查不出「谁导入了订单」。

    public static final String ORDER_IMPORT_EXECUTE = "ORDER_IMPORT_EXECUTE";
    public static final String PRODUCT_IMPORT_EXECUTE = "PRODUCT_IMPORT_EXECUTE";
    public static final String CUSTOMER_IMPORT_EXECUTE = "CUSTOMER_IMPORT_EXECUTE";
    public static final String LOGISTICS_IMPORT_EXECUTE = "LOGISTICS_IMPORT_EXECUTE";
    public static final String FINANCE_IMPORT_EXECUTE = "FINANCE_IMPORT_EXECUTE";
    public static final String SHOP_WAREHOUSE_IMPORT_EXECUTE = "SHOP_WAREHOUSE_IMPORT_EXECUTE";
    public static final String IMPORT_UPLOAD = "IMPORT_UPLOAD";
    public static final String IMPORT_FIELD_MAPPING_UPDATE = "IMPORT_FIELD_MAPPING_UPDATE";

    /**
     * 登录失败锁定统计的动作集合：只有认证类失败计入锁定，
     * 避免把业务校验失败也算成"爆破尝试"。
     */
    public static final List<String> AUTH_FAILURE_ACTIONS = List.of(AUTH_LOGIN);
}
