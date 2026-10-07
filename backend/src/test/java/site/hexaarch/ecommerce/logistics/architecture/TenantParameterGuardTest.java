package site.hexaarch.ecommerce.logistics.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import site.hexaarch.ecommerce.logistics.application.service.shop.ShopApplicationService;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.shop.repository.ShopRepository;
import site.hexaarch.ecommerce.logistics.domain.tenant.PermissionPoints;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.Role;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.shop.ShopJpaRepository;
import site.hexaarch.ecommerce.logistics.interfaces.controller.shop.ShopController;

/**
 * 租户隔离与店铺权限的架构守卫（ADR-0013 §4.3 三道锁的其中两道）。
 * <p>
 * 这套测试存在的理由不是「多一层保险」，而是当前隔离机制的形状决定的：
 * {@code TenantIsolationAspect} 是 {@code @Before}，它<b>只</b>比对「形参名恰好叫 {@code tenantId}」的
 * String 参数，不校验返回值、不过滤查询结果。于是有两处单点故障：
 * <ol>
 *   <li>参数名依赖 {@code -parameters} 编译标志。谁哪天清理 pom 把它关掉，
 *       {@code signature.getParameterNames()} 仍可能拿到 {@code arg0} 形状的名字，整套越权校验
 *       <b>静默失效且不报错</b>。测试 1 直接盯住这一点。</li>
 *   <li>服务方法忘了写 {@code tenantId} 形参，切面无从比对，等于该接口没有隔离。测试 2 盯住这一点。</li>
 * </ol>
 * 测试 3 把「仓储不提供无租户查询」从注释约定变成断言：这是编译期之外的第二道，
 * 因为 {@code JpaRepository} 自带的 {@code findById}/{@code findAll} 永远在，只能靠不调用它们。
 * </p>
 * <p>
 * 范围说明：测试 2 只覆盖 {@link ShopApplicationService}。存量服务里大量方法确实没有 {@code tenantId}
 * 形参（读路径靠控制器兜底），把它们一次性纳进来会让本测试长期红着从而失去意义；
 * 新增租户级服务时应把类名加进 {@link #TENANT_SCOPED_SERVICES}。
 * </p>
 *
 * @author kenyon
 */
class TenantParameterGuardTest {

    /**
     * 必须是租户级隔离的应用服务清单。
     */
    private static final List<Class<?>> TENANT_SCOPED_SERVICES = List.of(ShopApplicationService.class);

    /**
     * 必须是租户级隔离的店铺仓储清单。
     */
    private static final List<Class<?>> TENANT_SCOPED_REPOSITORIES =
            List.of(ShopRepository.class, ShopJpaRepository.class);

    private static final Pattern ARGUMENT_LIKE_NAME = Pattern.compile("^arg\\d+$");
    private static final Pattern LEGAL_PARAMETER_NAME = Pattern.compile("^[a-z][A-Za-z0-9]*$");
    private static final Pattern AUTHORITY_LITERAL = Pattern.compile("hasAuthority\\('([^']+)'\\)");
    private static final List<String> QUERY_PREFIXES = List.of("find", "exists", "count", "delete");

    @Test
    @DisplayName("-parameters 仍然生效：编译产物保留真实形参名")
    void compiledMetadataStillCarriesParameterNames() {
        List<String> violations = new ArrayList<>();
        for (Class<?> type : TENANT_SCOPED_SERVICES) {
            for (Method method : businessMethods(type)) {
                for (Parameter parameter : method.getParameters()) {
                    String name = parameter.getName();
                    if (ARGUMENT_LIKE_NAME.matcher(name).matches()) {
                        violations.add(type.getSimpleName() + "#" + method.getName()
                                + " 的参数名是 " + name + "（-parameters 已失效）");
                    } else if (!LEGAL_PARAMETER_NAME.matcher(name).matches()) {
                        violations.add(type.getSimpleName() + "#" + method.getName()
                                + " 的参数名 " + name + " 不是合法标识符形状");
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> String.join("; ", violations));

        // 光看形状还不够：至少要真的存在一个叫 tenantId 的参数，否则整套切面无从比对
        boolean anyTenantIdParameter = TENANT_SCOPED_SERVICES.stream()
                .flatMap(type -> businessMethods(type).stream())
                .flatMap(method -> Arrays.stream(method.getParameters()))
                .anyMatch(parameter -> "tenantId".equals(parameter.getName()));
        assertTrue(anyTenantIdParameter, "没有任何服务的形参叫 tenantId，TenantIsolationAspect 将无校验点");
    }

    @Test
    @DisplayName("租户级应用服务的每个 public 方法都必须带名为 tenantId 的形参")
    void tenantScopedServiceMethodsAllDeclareTenantId() {
        for (Class<?> type : TENANT_SCOPED_SERVICES) {
            List<String> offenders = businessMethods(type).stream()
                    .filter(method -> Arrays.stream(method.getParameters())
                            .noneMatch(parameter -> "tenantId".equals(parameter.getName())))
                    .map(method -> type.getSimpleName() + "#" + method.getName()
                            + "(" + Arrays.stream(method.getParameters())
                            .map(parameter -> parameter.getType().getSimpleName()).collect(Collectors.joining(","))
                            + ")")
                    .toList();
            assertTrue(offenders.isEmpty(), () -> type.getSimpleName() + " 缺少 tenantId 形参: "
                    + String.join(", ", offenders));
        }
    }

    @Test
    @DisplayName("店铺仓储不提供任何无租户条件的查询")
    void shopRepositoriesExposeNoTenantUnfilteredQueries() {
        for (Class<?> type : TENANT_SCOPED_REPOSITORIES) {
            List<String> offenders = Arrays.stream(type.getDeclaredMethods())
                    .filter(method -> !method.isSynthetic() && !method.isBridge())
                    .filter(method -> QUERY_PREFIXES.stream().anyMatch(prefix ->
                            method.getName().startsWith(prefix)))
                    .filter(method -> Arrays.stream(method.getParameters())
                            .noneMatch(parameter -> "tenantId".equals(parameter.getName())))
                    .map(method -> type.getSimpleName() + "#" + method.getName())
                    .toList();
            assertTrue(offenders.isEmpty(), () -> type.getSimpleName() + " 暴露了无租户条件的查询: "
                    + String.join(", ", offenders));
        }
        // 显式钉住两个反面例子方法名：一旦有人加回裸 findById/findAll，上面的前缀断言也会命中，
        // 但这两条把「为什么禁」写进失败信息里
        List<String> bareFinders = Arrays.stream(ShopRepository.class.getDeclaredMethods())
                .map(Method::getName)
                .filter(name -> name.equals("findById") || name.equals("findAll"))
                .toList();
        assertTrue(bareFinders.isEmpty(), "ShopRepository 不得出现裸 findById/findAll（ADR-0013 §4.3 锁 2）");
    }

    @Test
    @DisplayName("店铺控制器的每个端点都被 shop 权限点或平台管理员守住")
    void everyShopEndpointIsAuthorized() {
        List<Method> endpoints = businessMethods(ShopController.class).stream()
                .filter(method -> method.isAnnotationPresent(PreAuthorize.class))
                .toList();
        List<Method> ungated = businessMethods(ShopController.class).stream()
                .filter(method -> !method.isAnnotationPresent(PreAuthorize.class))
                .toList();
        assertFalse(endpoints.isEmpty(), "ShopController 上一个 @PreAuthorize 都没有");
        assertTrue(ungated.isEmpty(), () -> "未加权限门槛的端点: "
                + ungated.stream().map(Method::getName).collect(Collectors.joining(", ")));

        for (Method method : endpoints) {
            String expression = method.getAnnotation(PreAuthorize.class).value();
            assertNotNull(expression, method.getName() + " 的 @PreAuthorize 值为空");
            assertTrue(expression.contains("PLATFORM_ADMIN"),
                    () -> method.getName() + " 未允许平台管理员: " + expression);
            assertTrue(expression.contains("hasAuthority('shop:"),
                    () -> method.getName() + " 未引用 shop 权限点: " + expression);
        }
    }

    @Test
    @DisplayName("店铺四个权限点都在租户级 catalog 里且不进平台级 catalog")
    void shopPermissionsAreTenantScopedOnly() {
        List<String> shopPoints = List.of("shop:read", "shop:write", "shop:credential_write", "shop:sync");
        for (String point : shopPoints) {
            assertTrue(PermissionPoints.TENANT_SCOPED_PERMISSIONS.contains(point),
                    () -> point + " 不在 TENANT_SCOPED_PERMISSIONS 里，种子角色不会带上它");
            assertFalse(PermissionPoints.PLATFORM_SCOPED_PERMISSIONS.contains(point),
                    () -> point + " 不该出现在平台级 catalog");
        }
        assertEquals(shopPoints.size(),
                PermissionPoints.TENANT_SCOPED_PERMISSIONS.stream()
                        .filter(point -> point.startsWith("shop:")).count(),
                "shop 权限点数量与 catalog 不一致");
        assertTrue(PermissionPoints.ALL_PERMISSIONS.containsAll(PermissionPoints.TENANT_SCOPED_PERMISSIONS),
                "ALL_PERMISSIONS 未包含全部租户级权限点");
    }

    /**
     * 实测踩过的坑：catalog 加了 {@code shop:credential:write}（三段），而 {@link Role} 的格式校验只收两段，
     * {@code addPermission} 抛 {@code PERMISSION_INVALID}，{@code resetPermissions} 整批回滚，
     * 种子逻辑的 {@code catch (Exception)} 把它压成一条 ERROR 日志——于是所有角色都停在旧清单上，
     * 新权限点对应接口永久 403，而测试全绿。这条断言把该失效形态变成编译期的红。
     */
    @Test
    @DisplayName("catalog 里每个权限点都能通过 Role 的格式校验")
    void everyPermissionPointPassesRoleValidation() {
        List<String> rejected = new ArrayList<>();
        for (String point : PermissionPoints.ALL_PERMISSIONS) {
            try {
                Role.validatePermission(point);
            } catch (BusinessException e) {
                rejected.add(point + " -> " + e.getMessage());
            }
        }
        assertTrue(rejected.isEmpty(), () -> "这些权限点写不进角色，种子会整批失败: " + String.join("; ", rejected));

        assertEquals(PermissionPoints.TENANT_SCOPED_PERMISSIONS.size()
                        + PermissionPoints.PLATFORM_SCOPED_PERMISSIONS.size(),
                PermissionPoints.ALL_PERMISSIONS.size(),
                "两段 catalog 有重复权限点，ALL_PERMISSIONS 数量对不上");
    }

    /**
     * {@code @PreAuthorize} 里写的权限点必须存在于 catalog：否则该点永远不会出现在任何角色的
     * GrantedAuthority 中，接口对租户管理员恒 403，且没有任何日志指向原因。
     */
    @Test
    @DisplayName("控制器引用的权限点都在 catalog 中")
    void everyReferencedPermissionExistsInCatalog() {
        int referenced = 0;
        for (Method method : businessMethods(ShopController.class)) {
            PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
            if (annotation == null) {
                continue;
            }
            Matcher matcher = AUTHORITY_LITERAL.matcher(annotation.value());
            while (matcher.find()) {
                String point = matcher.group(1);
                referenced++;
                assertTrue(PermissionPoints.ALL_PERMISSIONS.contains(point),
                        () -> method.getName() + " 引用了权限点 " + point + "，但它不在 catalog 里，该端点无人可访问");
            }
        }
        assertTrue(referenced > 0, "ShopController 没有引用任何 hasAuthority 权限点");
    }

    /**
     * 只取业务方法：public、非静态、非合成/桥接，且排除 Object 自带方法与构造器。
     */
    private static List<Method> businessMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .filter(method -> !method.isSynthetic() && !method.isBridge())
                .toList();
    }
}
