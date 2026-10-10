package site.hexaarch.ecommerce.logistics.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import site.hexaarch.ecommerce.logistics.domain.tenant.PermissionPoints;
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
import site.hexaarch.ecommerce.logistics.interfaces.controller.overview.OverviewController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.platform.PlatformIntegrationController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.product.ProductController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.product.ProductImportController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.purchase.PurchaseController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.shop.ShopController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.tenant.RoleController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.tenant.UserController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.warehouse.ShopWarehouseController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.warehouse.WarehouseController;
import site.hexaarch.ecommerce.logistics.interfaces.controller.warehouse.WarehouseImportController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 权限点覆盖的架构守卫（tenant-and-iam §8 落地步骤 ③、§10 验收要点 2）。
 * <p>
 * 这一层要治的是「种了权限点却没人检查」：目录里的点配给角色后界面全绿、接口照通，
 * 管理员以为收回了权限，实际只是改了数据。security-design §5 实测过这个状态
 * （26 个点里只有 14 个被 {@code hasAuthority()} 引用）。守卫把口径变成会红的测试：
 * <ol>
 *   <li>catalog 每一项都必须被至少一个端点检查；</li>
 *   <li>业务控制器的每个端点都必须被 {@code @PreAuthorize}（方法级或类级）守住；</li>
 *   <li>注解引用的点都必须存在于 catalog（反向口径，覆盖全部业务控制器，
 *       {@link TenantParameterGuardTest} 只扫 ShopController）。</li>
 * </ol>
 * 白名单只允许收缩不允许扩张，且每条必须写理由。
 * </p>
 *
 * @author kenyon
 */
class PermissionPointCoverageGuardTest {

    /**
     * 必须"每个端点都有权限检查"的控制器清单。
     * {@code AuthController}（登录/刷新/登出/me 由过滤器链 permitAll+authenticated 管）与
     * {@code TenantController}（{@code /api/tenants/**} 在 SecurityConfig 里整段
     * {@code hasRole('PLATFORM_ADMIN')}）不在列，它们由链路规则而非注解守护。
     */
    private static final List<Class<?>> BUSINESS_CONTROLLERS = List.of(
            OrderController.class, OrderImportController.class,
            LogisticsController.class, LogisticsImportController.class, LogisticsChannelController.class,
            ProductController.class, ProductImportController.class,
            CustomerController.class, CustomerImportController.class,
            WarehouseController.class, WarehouseImportController.class, ShopWarehouseController.class,
            FinanceController.class, FinanceImportController.class,
            PurchaseController.class, ShopController.class, PlatformIntegrationController.class,
            OverviewController.class, UserController.class, RoleController.class,
            SystemConfigController.class, AuditLogController.class);

    /**
     * 不靠权限点守的端点：值必须写清理由，否则等于给守卫开后门。
     */
    private static final Map<String, String> ENDPOINT_EXEMPTIONS = Map.of(
            PlatformIntegrationController.class.getSimpleName() + "#authSpecs",
            "平台认证方式字典，纯静态元数据，ADR-0013 §4.2 明确「读放开」",
            PlatformIntegrationController.class.getSimpleName() + "#getAllPlatformIntegrations",
            "对接列表出参已剥离我方 app 凭据，读放开、写仍 platform:manage（ADR-0013 §4.2）",
            PlatformIntegrationController.class.getSimpleName() + "#getPlatformIntegrationById",
            "同上，单条详情与列表同一口径");

    private static final Pattern AUTHORITY_LITERAL = Pattern.compile("hasAuthority\\('([^']+)'\\)");

    @Test
    @DisplayName("catalog 里每个权限点都真的被某个端点检查")
    void everyCatalogPermissionIsEnforcedByAnEndpoint() {
        Set<String> enforced = new LinkedHashSet<>();
        for (Class<?> controller : BUSINESS_CONTROLLERS) {
            expressionsOf(controller).forEach(expression ->
                    AUTHORITY_LITERAL.matcher(expression).results()
                            .map(m -> m.group(1)).forEach(enforced::add));
        }
        List<String> seededButDead = PermissionPoints.ALL_PERMISSIONS.stream()
                .filter(point -> !enforced.contains(point))
                .collect(Collectors.toList());
        assertTrue(seededButDead.isEmpty(),
                () -> "这些权限点只种在角色数据里、没有任何端点检查，配了也不生效：" + seededButDead
                        + "。要么给对应端点补 @PreAuthorize，要么把它们从 catalog 删掉");
    }

    @Test
    @DisplayName("业务控制器的每个端点都被 @PreAuthorize 守住")
    void everyBusinessEndpointIsAuthorized() {
        List<String> ungated = new ArrayList<>();
        for (Class<?> controller : BUSINESS_CONTROLLERS) {
            boolean classLevel = controller.isAnnotationPresent(PreAuthorize.class);
            for (Method method : endpointMethods(controller)) {
                String key = controller.getSimpleName() + "#" + method.getName();
                if (method.isAnnotationPresent(PreAuthorize.class) || classLevel
                        || ENDPOINT_EXEMPTIONS.containsKey(key)) {
                    continue;
                }
                ungated.add(key + " " + mappingDescription(method));
            }
        }
        // 白名单指向的代码消失即报错，防止白名单变成永久后门
        Set<String> stillThere = new TreeSet<>();
        for (Class<?> controller : BUSINESS_CONTROLLERS) {
            endpointMethods(controller).forEach(method -> {
                String key = controller.getSimpleName() + "#" + method.getName();
                if (ENDPOINT_EXEMPTIONS.containsKey(key)) {
                    stillThere.add(key);
                }
            });
        }
        assertTrue(ungated.isEmpty(), () -> "这些端点没有任何权限检查，登录即可调用：" + ungated);
        assertTrue(stillThere.containsAll(ENDPOINT_EXEMPTIONS.keySet()),
                () -> "白名单里有已不存在的端点，请同步删除条目：" + ENDPOINT_EXEMPTIONS.keySet().stream()
                        .filter(key -> !stillThere.contains(key)).collect(Collectors.toList()));
    }

    @Test
    @DisplayName("注解引用的权限点都在 catalog 中")
    void everyReferencedPermissionExistsInCatalog() {
        Set<String> referenced = new LinkedHashSet<>();
        BUSINESS_CONTROLLERS.stream().map(this::expressionsOf).flatMap(List::stream)
                .forEach(expression -> AUTHORITY_LITERAL.matcher(expression).results()
                        .map(m -> m.group(1)).forEach(referenced::add));
        List<String> orphans = referenced.stream()
                .filter(point -> !PermissionPoints.ALL_PERMISSIONS.contains(point))
                .collect(Collectors.toList());
        assertTrue(orphans.isEmpty(),
                () -> "这些点被 @PreAuthorize 引用却不在 catalog 里，任何角色都拿不到，端点恒 403：" + orphans);
    }

    /**
     * 控制器类级与方法级注解里的 SpEL 表达式。常量在编译期内联进注解，反射读得到全文。
     */
    private List<String> expressionsOf(Class<?> controller) {
        List<String> expressions = new ArrayList<>();
        PreAuthorize classLevel = controller.getAnnotation(PreAuthorize.class);
        if (classLevel != null) {
            expressions.add(classLevel.value());
        }
        for (Method method : endpointMethods(controller)) {
            PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
            if (annotation != null) {
                expressions.add(annotation.value());
            }
        }
        return expressions;
    }

    /**
     * 带请求映射的 public 方法，即真正的 HTTP 端点。
     */
    private List<Method> endpointMethods(Class<?> controller) {
        return Arrays.stream(controller.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic() && !method.isBridge())
                .filter(PermissionPointCoverageGuardTest::isEndpoint)
                .toList();
    }

    private static boolean isEndpoint(Method method) {
        return method.isAnnotationPresent(GetMapping.class)
                || method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class)
                || method.isAnnotationPresent(PatchMapping.class);
    }

    private static String mappingDescription(Method method) {
        List<String> verbs = new ArrayList<>();
        if (method.isAnnotationPresent(GetMapping.class)) {
            verbs.add("GET");
        }
        if (method.isAnnotationPresent(PostMapping.class)) {
            verbs.add("POST");
        }
        if (method.isAnnotationPresent(PutMapping.class)) {
            verbs.add("PUT");
        }
        if (method.isAnnotationPresent(DeleteMapping.class)) {
            verbs.add("DELETE");
        }
        if (method.isAnnotationPresent(PatchMapping.class)) {
            verbs.add("PATCH");
        }
        return String.join("/", verbs);
    }
}
