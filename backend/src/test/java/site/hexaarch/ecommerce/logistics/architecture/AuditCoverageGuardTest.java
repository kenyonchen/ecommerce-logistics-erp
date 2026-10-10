package site.hexaarch.ecommerce.logistics.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import site.hexaarch.ecommerce.logistics.application.service.tenant.LoginSecurityService;
import site.hexaarch.ecommerce.logistics.domain.audit.AuditActions;
import site.hexaarch.ecommerce.logistics.domain.audit.Auditable;
import site.hexaarch.ecommerce.logistics.interfaces.controller.audit.AuditLogController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.config.SystemConfigController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.customer.CustomerController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.customer.CustomerImportController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.finance.FinanceController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.finance.FinanceImportController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.logistics.LogisticsChannelController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.logistics.LogisticsController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.logistics.LogisticsImportController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.order.OrderController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.order.OrderImportController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.platform.PlatformIntegrationController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.product.ProductController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.product.ProductImportController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.purchase.PurchaseController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.shop.ShopController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.tenant.AuthController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.tenant.RoleController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.tenant.TenantController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.tenant.UserController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.warehouse.ShopWarehouseController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.warehouse.WarehouseController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.warehouse.WarehouseImportController;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 审计覆盖的架构守卫（BR-12 / security-design §7 的「新增敏感端点必须留痕」）。
 * <p>
 * 为什么必须有这一层：{@code @Auditable} 是漏了也不会报错的那类约定。少标一个改权限的端点，
 * 接口照样 200，功能测试照样绿，只有在出事之后翻审计时才看得见那条空白。
 * 这条守卫把「敏感端点是否留痕」变成编译期之后的红，思路与 {@link TenantParameterGuardTest} 一致。
 * </p>
 * <p>
 * 清单口径（ISS-123 / R-37 收窄后）：<b>写端点一律留痕，豁免必须写明理由</b>。
 * security-design §9 第 8 项那句「身份/权限/金额/凭据变更一律审计」只是下限 —— 客户 PII、
 * 商品与 SKU、物流渠道报价、店铺仓库绑定同样是需要事后追责的写操作，实测里正是这几族端点
 * 56 个全部没有留痕（导入线连"谁导进了什么"都查不到）。因此主数据与导入线控制器已一并纳管。
 * </p>
 * <p>
 * 认证链路（登录、刷新）在这里是<b>白名单</b>而不是遗漏：那时 SecurityContext 还没建立，
 * 注解取不到操作人，改由 {@link LoginSecurityService} 显式留痕。白名单必须写理由，
 * 且只允许收缩不允许扩张。
 * </p>
 *
 * @author kenyon
 */
class AuditCoverageGuardTest {

    /**
     * 必须做到「改数据的端点全部留痕」的控制器清单。
     */
    private static final List<Class<?>> AUDITED_CONTROLLERS = List.of(
            UserController.class, RoleController.class, TenantController.class,
            AuthController.class, ShopController.class, PlatformIntegrationController.class,
            SystemConfigController.class,
            // 金额/库存/单据状态：§9 第 8 项口径的后半段（ISS-114）
            FinanceController.class, PurchaseController.class, OrderController.class,
            WarehouseController.class, LogisticsController.class,
            // 主数据 / 渠道 / 店铺仓库绑定 / 导入线与平台同步（ISS-123 / R-37）：
            // 这 10 个控制器原先完全不在清单里，56 个写端点一条留痕都没有
            CustomerController.class, ProductController.class, LogisticsChannelController.class,
            ShopWarehouseController.class,
            OrderImportController.class, ProductImportController.class, CustomerImportController.class,
            LogisticsImportController.class, FinanceImportController.class, WarehouseImportController.class);

    /**
     * 预览只把文件解析成行回给前端确认映射，一行都不落库；真正落库的 upload(mode=import)
     * 与 execute 端点都带注解（IMPORT_UPLOAD / *_IMPORT_EXECUTE）。
     */
    private static final String PREVIEW_EXEMPTION = "只解析文件返回预览行与列映射，不落库；导入执行走 IMPORT_UPLOAD / *_IMPORT_EXECUTE 留痕";

    /**
     * 不靠注解留痕的写端点：值必须写清原因，否则等于给守卫开后门。
     * <p>
     * 只允许收缩：原先这里有 7 条平台集成同步/探活/回写的豁免，理由是"作业记录即留痕"——
     * 实际取证时发现导入作业查不到"谁在什么时候对哪个集成触发的同步"，于是 ISS-123 把它们改成注解留痕，
     * 这 7 条豁免随之删除。留着会让"以后有人摘掉注解"这件事重新变成静默通过。
     * </p>
     */
    private static final Map<String, String> ANNOTATION_EXEMPTIONS = Map.of(
            AuthController.class.getSimpleName() + "#login",
            "认证前无 SecurityContext，操作人由 LoginSecurityService.recordLoginAttempt 显式写入",
            AuthController.class.getSimpleName() + "#refreshToken",
            "同上，刷新链路用 AUTH_REFRESH 动作码显式留痕，与登录失败锁定互不污染",
            OrderImportController.class.getSimpleName() + "#preview", PREVIEW_EXEMPTION,
            ProductImportController.class.getSimpleName() + "#preview", PREVIEW_EXEMPTION,
            CustomerImportController.class.getSimpleName() + "#preview", PREVIEW_EXEMPTION,
            LogisticsImportController.class.getSimpleName() + "#preview", PREVIEW_EXEMPTION,
            FinanceImportController.class.getSimpleName() + "#preview", PREVIEW_EXEMPTION,
            WarehouseImportController.class.getSimpleName() + "#preview", PREVIEW_EXEMPTION);

    private static final Pattern ACTION_PATTERN = Pattern.compile("^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$");

    /**
     * 由认证链路显式记录、不会出现在注解里的动作码。
     */
    private static final Set<String> EXPLICITLY_RECORDED_ACTIONS =
            new LinkedHashSet<>(List.of(AuditActions.AUTH_LOGIN, AuditActions.AUTH_REFRESH));

    @Test
    @DisplayName("敏感控制器的每个写端点都留痕，或在不扩张的白名单里写明理由")
    void everyMutatingEndpointIsAuditedOrExemptWithReason() {
        List<String> untracked = unauditedKeys(AUDITED_CONTROLLERS);
        List<String> exemptWithoutReason = AUDITED_CONTROLLERS.stream()
                .flatMap(controller -> businessMethods(controller).stream()
                        .filter(AuditCoverageGuardTest::isMutatingHandler)
                        .filter(method -> !method.isAnnotationPresent(Auditable.class))
                        .map(method -> controller.getSimpleName() + "#" + method.getName())
                        .filter(ANNOTATION_EXEMPTIONS::containsKey)
                        .filter(key -> ANNOTATION_EXEMPTIONS.get(key).isBlank()))
                .toList();

        assertTrue(untracked.isEmpty(), () -> "这些写端点没有 @Auditable，也不在白名单里: "
                + String.join(", ", untracked));
        assertTrue(exemptWithoutReason.isEmpty(), () -> "白名单项必须有理由: "
                + String.join(", ", exemptWithoutReason));

        // 白名单里的键必须真实存在：端点改名后白名单要跟着清，否则它会悄悄失去约束力
        Set<String> actualMutatingKeys = AUDITED_CONTROLLERS.stream()
                .flatMap(controller -> businessMethods(controller).stream()
                        .filter(AuditCoverageGuardTest::isMutatingHandler)
                        .map(method -> controller.getSimpleName() + "#" + method.getName()))
                .collect(Collectors.toSet());
        List<String> stale = ANNOTATION_EXEMPTIONS.keySet().stream()
                .filter(key -> !actualMutatingKeys.contains(key))
                .toList();
        assertTrue(stale.isEmpty(), () -> "白名单里有已不存在的端点（改名或删除后请一并删除该项）: "
                + String.join(", ", stale));
    }

    @Test
    @DisplayName("注解里用的动作码必须是 AuditActions 常量，且常量不得成为无人使用的死码")
    void actionCodesAreSharedConstantsAndAllUsed() {
        Set<String> declared = declaredActionCodes();

        List<String> usedOutsideCatalog = new java.util.ArrayList<>();
        Set<String> usedInAnnotations = new LinkedHashSet<>();
        for (Class<?> controller : AUDITED_CONTROLLERS) {
            for (Method method : businessMethods(controller)) {
                Auditable auditable = method.getAnnotation(Auditable.class);
                if (auditable == null) {
                    continue;
                }
                usedInAnnotations.add(auditable.action());
                if (!declared.contains(auditable.action())) {
                    usedOutsideCatalog.add(controller.getSimpleName() + "#" + method.getName()
                            + " -> " + auditable.action());
                }
            }
        }
        assertTrue(usedOutsideCatalog.isEmpty(), () -> "动作码必须是 AuditActions 常量（拼错的动作码只会在检索时静默漏掉）: "
                + String.join(", ", usedOutsideCatalog));

        List<String> neverUsed = declared.stream()
                .filter(code -> !EXPLICITLY_RECORDED_ACTIONS.contains(code))
                .filter(code -> !usedInAnnotations.contains(code))
                .toList();
        assertTrue(neverUsed.isEmpty(), () -> "这些动作码没有任何端点在用，要么接上要么删除: "
                + String.join(", ", neverUsed));
    }

    @Test
    @DisplayName("每条留痕都有中文动作文案，动作码为大写蛇形")
    void everyAnnotationCarriesDisplayableLabel() {
        List<String> offenders = new java.util.ArrayList<>();
        int annotated = 0;
        for (Class<?> controller : AUDITED_CONTROLLERS) {
            for (Method method : businessMethods(controller)) {
                Auditable auditable = method.getAnnotation(Auditable.class);
                if (auditable == null) {
                    continue;
                }
                annotated++;
                if (auditable.label().isBlank()) {
                    offenders.add(controller.getSimpleName() + "#" + method.getName() + " 缺少 label");
                }
                if (!ACTION_PATTERN.matcher(auditable.action()).matches()) {
                    offenders.add(controller.getSimpleName() + "#" + method.getName()
                            + " 动作码形状不合法: " + auditable.action());
                }
                if (auditable.detail().contains("password") || auditable.detail().contains("token")) {
                    offenders.add(controller.getSimpleName() + "#" + method.getName()
                            + " 的 detail 疑似写入凭据，审计表不得存敏感值");
                }
            }
        }
        assertTrue(annotated >= 25, "注解数量异常（实际 " + annotated + "），是不是批量丢失了？");
        assertTrue(offenders.isEmpty(), () -> String.join("; ", offenders));
    }

    @Test
    @DisplayName("审计只读接口对平台管理员以外关闭（行内含操作人/IP/失败原因）")
    void auditReadEndpointsArePlatformAdminOnly() {
        PreAuthorize classGate = AuditLogController.class.getAnnotation(PreAuthorize.class);
        assertTrue(classGate != null && classGate.value().contains("PLATFORM_ADMIN"),
                "AuditLogController 必须有类级平台管理员门槛");

        List<String> ungated = businessMethods(AuditLogController.class).stream()
                .filter(method -> !method.isAnnotationPresent(org.springframework.web.bind.annotation.GetMapping.class))
                .map(Method::getName)
                .toList();
        assertTrue(ungated.isEmpty(), () -> "审计仓储只允许追加，控制器不得出现写端点: "
                + String.join(", ", ungated));
    }

    /**
     * 登录留痕的存活条件：{@code AuthController} 一旦不再持有 {@link LoginSecurityService}，
     * 登录成功/失败就不再落 audit_logs，而锁定判断也会跟着失效（它读的就是这张表）。
     * 这条断言把「摘掉依赖」变成测试红，而不是变成一次事后无从追溯的登录。
     */
    @Test
    @DisplayName("认证控制器仍持有登录安全服务，登出仍带注解留痕")
    void authControllerStillWiresLoginSecurity() {
        boolean wired = Arrays.stream(AuthController.class.getDeclaredConstructors())
                .map(Constructor::getParameterTypes)
                .flatMap(Arrays::stream)
                .anyMatch(LoginSecurityService.class::equals);
        assertTrue(wired, "AuthController 不再注入 LoginSecurityService，登录链路审计与失败锁定同时失效");

        Auditable logout = findMethod(AuthController.class, "logout").getAnnotation(Auditable.class);
        assertTrue(logout != null && AuditActions.AUTH_LOGOUT.equals(logout.action()),
                "登出必须留痕，否则「谁把令牌注销了」无从查证");
        assertFalse(EXPLICITLY_RECORDED_ACTIONS.contains(AuditActions.AUTH_LOGOUT),
                "登出发生在认证之后，走注解即可，不要和登录链路混成两套口径");
    }

    /**
     * 守卫自检（负例）。上面几条断言都是「集合为空才通过」的形状，一旦判定逻辑退化 ——
     * 比如 {@code isMutatingHandler} 少认一个注解、或白名单键拼错导致查询恒不命中 ——
     * 它们会变成永远为真的空转，而守卫静默失效比没有守卫更糟。
     * 这条把同一套判定跑在故意漏标注解的夹具上：必须报出漏的两个，且不得误报标了的与只读的。
     */
    @Test
    @DisplayName("守卫自检：漏标注解的写端点必须被报出来，已标注与只读的不得混进缺口")
    void guardStillDetectsMissingAnnotation() {
        assertEquals(List.of(
                        "FixtureController#createWithoutAudit",
                        "FixtureController#patchWithoutAudit",
                        "FixtureController#updateWithoutAudit"),
                unauditedKeys(List.of(FixtureController.class)).stream().sorted().toList(),
                "判定逻辑没把夹具里漏标注解的写端点认出来，守卫已空转");
    }

    // ------------------------------------------------------------------ 夹具

    /**
     * 只给 {@link #guardStillDetectsMissingAnnotation()} 用的假控制器，不在纳管清单里。
     */
    @SuppressWarnings("unused")
    static class FixtureController {

        @PostMapping("/fixture")
        public void createWithoutAudit() {
        }

        @PutMapping("/fixture/{id}")
        public void updateWithoutAudit(String id) {
        }

        @PatchMapping("/fixture/{id}")
        public void patchWithoutAudit(String id) {
        }

        @DeleteMapping("/fixture/{id}")
        @Auditable(action = AuditActions.PRODUCT_DELETE, label = "夹具删除留痕",
                targetType = "FIXTURE", targetIdParam = "id")
        public void deleteWithAudit(String id) {
        }

        @GetMapping("/fixture")
        public void readOnlyIsNotMutating() {
        }
    }

    private static List<String> unauditedKeys(List<Class<?>> controllers) {
        List<String> untracked = new ArrayList<>();
        for (Class<?> controller : controllers) {
            for (Method method : businessMethods(controller)) {
                if (!isMutatingHandler(method) || method.isAnnotationPresent(Auditable.class)) {
                    continue;
                }
                String key = controller.getSimpleName() + "#" + method.getName();
                if (!ANNOTATION_EXEMPTIONS.containsKey(key)) {
                    untracked.add(key);
                }
            }
        }
        return untracked;
    }

    private static Set<String> declaredActionCodes() {
        return Arrays.stream(AuditActions.class.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers()))
                .filter(field -> field.getType() == String.class)
                .map(field -> {
                    try {
                        return (String) field.get(null);
                    } catch (IllegalAccessException ex) {
                        throw new AssertionError("动作码常量不可读: " + field.getName(), ex);
                    }
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Method findMethod(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("方法不存在: " + type.getSimpleName() + "#" + name));
    }

    private static boolean isMutatingHandler(Method method) {
        return method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(PatchMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class);
    }

    private static List<Method> businessMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .filter(method -> !method.isSynthetic() && !method.isBridge())
                .toList();
    }
}
