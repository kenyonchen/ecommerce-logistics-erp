package site.hexaarch.ecommerce.logistics.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.application.service.tenant.RoleServiceImpl;
import site.hexaarch.ecommerce.logistics.application.service.tenant.TenantServiceImpl;
import site.hexaarch.ecommerce.logistics.application.service.tenant.UserServiceImpl;
import site.hexaarch.ecommerce.logistics.domain.logistics.entity.LogisticsChannel;
import site.hexaarch.ecommerce.logistics.domain.logistics.service.LogisticsChannelService;
import site.hexaarch.ecommerce.logistics.domain.tenant.PermissionPoints;
import site.hexaarch.ecommerce.logistics.domain.tenant.RoleNames;
import site.hexaarch.ecommerce.logistics.domain.tenant.aggregate.Tenant;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.Role;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.User;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.TenantRepository;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.UserRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 数据初始化配置，用于在应用启动时检查并创建默认租户、管理员用户、平台管理员角色与内置物流渠道。
 *
 * @author kenyon
 */
@Component
public class DataInitializationConfig implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializationConfig.class);

    /**
     * 平台管理员系统角色名（authority: ROLE_PLATFORM_ADMIN）。
     * 角色名统一由 {@link RoleNames} 定义，此处不再出现字面量。
     */
    private static final String PLATFORM_ADMIN_ROLE = RoleNames.PLATFORM_ADMIN;

    /**
     * 内置物流渠道：channelCode 是平台物流数据导入时的匹配依据，
     * 同时 {@code DEFAULT_CHANNEL} 供消息消费与流程编排链路的「默认渠道」占位使用。
     */
    private static final List<SeedChannel> SEED_LOGISTICS_CHANNELS = List.of(
            new SeedChannel("DEFAULT_CHANNEL", "默认渠道", "系统内置默认渠道，供未指定渠道的流程使用",
                    List.of(), new BigDecimal("10.00"), new BigDecimal("0.01")),
            new SeedChannel("SF", "顺丰速运", "国内时效件", List.of("CN"),
                    new BigDecimal("23.00"), new BigDecimal("0.02")),
            new SeedChannel("ZTO", "中通快递", "经济件", List.of("CN"),
                    new BigDecimal("8.00"), new BigDecimal("0.005")),
            new SeedChannel("YTO", "圆通速递", "经济件", List.of("CN"),
                    new BigDecimal("8.00"), new BigDecimal("0.005")),
            new SeedChannel("JD", "京东物流", "电商仓配一体", List.of("CN"),
                    new BigDecimal("12.00"), new BigDecimal("0.01")),
            new SeedChannel("EMS", "邮政EMS", "跨境及国内", List.of("CN", "US", "DE", "JP"),
                    new BigDecimal("20.00"), new BigDecimal("0.015")),
            new SeedChannel("USPS", "USPS", "美国邮政", List.of("US"),
                    new BigDecimal("5.00"), new BigDecimal("0.03")),
            new SeedChannel("DHL", "DHL", "国际快递", List.of("US", "DE", "JP", "GB"),
                    new BigDecimal("15.00"), new BigDecimal("0.06"))
    );

    private final TenantServiceImpl tenantService;
    private final TenantRepository tenantRepository;
    private final RoleServiceImpl roleService;
    private final UserServiceImpl userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LogisticsChannelService logisticsChannelService;

    @Autowired
    public DataInitializationConfig(TenantServiceImpl tenantService,
                                    TenantRepository tenantRepository,
                                    RoleServiceImpl roleService,
                                    UserServiceImpl userService,
                                    UserRepository userRepository,
                                    PasswordEncoder passwordEncoder,
                                    LogisticsChannelService logisticsChannelService) {
        this.tenantService = tenantService;
        this.tenantRepository = tenantRepository;
        this.roleService = roleService;
        this.userService = userService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.logisticsChannelService = logisticsChannelService;
    }

    /**
     * 存量数据迁移：把历史遗留的租户管理员角色 {@code ADMIN} 统一改名为 {@code TENANT_ADMIN}。
     * <p>
     * 背景：早期版本租户管理员角色名是 {@code ADMIN}，而前端 {@code TenantAdminGuard}
     * 期望的是 {@code TENANT_ADMIN}，导致同语义两个角色名并存，登录后被守卫判为无权限。
     * 改名而非新建，可保留 {@code user_roles} 上已有的用户绑定关系。
     * </p>
     * <p>
     * 若某租户已同时存在两个角色，则把遗留角色上的用户改挂到 {@code TENANT_ADMIN} 后删除遗留角色，
     * 避免出现两个语义重复的租户管理员角色。
     * </p>
     */
    private void migrateLegacyTenantAdminRole() {
        try {
            int migrated = 0;
            for (Tenant tenant : tenantRepository.findAll()) {
                Optional<Role> legacyOpt = roleService.findByName(tenant.getId(), RoleNames.LEGACY_TENANT_ADMIN);
                if (legacyOpt.isEmpty()) {
                    continue;
                }
                Role legacy = legacyOpt.get();
                Optional<Role> currentOpt = roleService.findByName(tenant.getId(), RoleNames.TENANT_ADMIN);
                if (currentOpt.isEmpty()) {
                    // 该租户尚无 TENANT_ADMIN：直接改名，保留原有用户绑定
                    roleService.updateRole(legacy.getId(), RoleNames.TENANT_ADMIN,
                            RoleNames.TENANT_ADMIN_DESCRIPTION);
                } else {
                    // 已有 TENANT_ADMIN：先把遗留角色上的用户改挂过去，再删除遗留角色
                    Role target = currentOpt.get();
                    for (User user : userRepository.findByTenantId(tenant.getId())) {
                        if (!hasRoleNamed(user, RoleNames.LEGACY_TENANT_ADMIN)
                                || hasRoleNamed(user, RoleNames.TENANT_ADMIN)) {
                            continue;
                        }
                        userService.assignRoleToUser(user.getId(), target.getId());
                    }
                    roleService.deleteRole(legacy.getId());
                }
                migrated++;
            }
            if (migrated > 0) {
                logger.info("已把 {} 个租户的租户管理员角色由 {} 迁移为 {}",
                        migrated, RoleNames.LEGACY_TENANT_ADMIN, RoleNames.TENANT_ADMIN);
            }
        } catch (Exception e) {
            logger.error("迁移租户管理员角色名时发生错误", e);
        }
    }

    /**
     * 存量数据回填：把所有 {@code TENANT_ADMIN} 角色的权限点对齐到租户级全量清单。
     * <p>
     * 背景：早期版本 {@code ADMIN} 角色创建时不带任何权限点，叠加控制器上
     * {@code hasRole('PLATFORM_ADMIN') or hasAuthority(...)} 的 OR 结构，
     * 导致租户管理员「菜单几乎全可见、但绝大多数接口 403」。这里做幂等对齐。
     * </p>
     */
    private void ensureTenantAdminPermissions() {
        try {
            int aligned = 0;
            for (Tenant tenant : tenantRepository.findAll()) {
                Optional<Role> tenantAdmin = roleService.findByName(tenant.getId(), RoleNames.TENANT_ADMIN);
                if (tenantAdmin.isEmpty()) {
                    continue;
                }
                roleService.setPermissionsToRole(tenantAdmin.get().getId(), PermissionPoints.tenantAdminPermissions());
                aligned++;
            }
            if (aligned > 0) {
                logger.info("已把 {} 个租户管理员角色的权限点对齐为租户级全量清单（{} 项）",
                        aligned, PermissionPoints.tenantAdminPermissions().size());
            }
        } catch (Exception e) {
            logger.error("回填租户管理员角色权限点时发生错误", e);
        }
    }

    private boolean hasRoleNamed(User user, String roleName) {
        return user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> roleName.equals(role.getName()));
    }

    /**
     * 内置物流渠道种子数据
     */
    private record SeedChannel(String channelCode, String channelName, String description, List<String> countries,
                               BigDecimal basePrice, BigDecimal pricePerGram) {
    }

    @Override
    public void run(String... args) throws Exception {
        initializeDefaultTenantAndUser();
        ensurePlatformAdminRole();
        ensureAdminHasPlatformAdminRole();
        migrateLegacyTenantAdminRole();
        ensureTenantAdminPermissions();
        seedLogisticsChannels();
    }

    /**
     * 初始化默认租户和管理员用户
     */
    private void initializeDefaultTenantAndUser() {
        try {
            // 检查是否已存在租户
            var existingTenants = tenantRepository.findAll();

            if (existingTenants.isEmpty()) {
                logger.info("未检测到现有租户，开始创建默认租户和管理员用户...");

                // 创建默认租户（一步式：同时创建租户管理员账号，密码由服务层加密）
                Tenant defaultTenant = tenantService.createTenant(
                        "默认租户",
                        "系统默认创建的租户",
                        "admin@example.com",
                        "13800138000",
                        "admin",
                        "123456",
                        "admin@example.com",
                        "系统",
                        "管理员"
                );

                logger.info("默认租户与管理员创建成功，租户ID: {}", defaultTenant.getId());
                logger.info("默认管理员用户名: admin");
                logger.info("默认密码: 123456");
                logger.info("请在生产环境中及时修改默认密码！");

            } else {
                logger.info("检测到 {} 个现有租户，跳过默认租户创建", existingTenants.size());
            }
        } catch (Exception e) {
            logger.error("初始化默认租户和用户时发生错误", e);
        }
    }

    /**
     * 确保存在 PLATFORM_ADMIN 系统角色并挂载全量权限点（幂等，兼容已有数据库）
     */
    private void ensurePlatformAdminRole() {
        try {
            Role platformAdmin = findSystemRole(PLATFORM_ADMIN_ROLE);
            if (platformAdmin == null) {
                platformAdmin = roleService.createSystemRole(
                        PLATFORM_ADMIN_ROLE, RoleNames.PLATFORM_ADMIN_DESCRIPTION);
                logger.info("系统角色 {} 创建成功", PLATFORM_ADMIN_ROLE);
            }

            // 权限点不足时补齐（幂等）。按集合比对而非数量：catalog 改名或替换权限点时总数不变，
            // 用 size 判断会认为「已经齐了」，新权限点永远进不了角色。
            List<String> expected = PermissionPoints.platformAdminPermissions();
            if (platformAdmin.getPermissions() == null
                    || !platformAdmin.getPermissions().containsAll(expected)) {
                roleService.setPermissionsToRole(platformAdmin.getId(), expected);
                logger.info("系统角色 {} 权限点已对齐为 {} 项", PLATFORM_ADMIN_ROLE, expected.size());
            }
        } catch (Exception e) {
            logger.error("初始化平台管理员角色时发生错误", e);
        }
    }

    /**
     * 确保 admin 用户持有 PLATFORM_ADMIN 角色（幂等，兼容已有数据库）
     */
    private void ensureAdminHasPlatformAdminRole() {
        try {
            Optional<User> adminOpt = userRepository.findByUsername("admin");
            if (adminOpt.isEmpty()) {
                return;
            }
            User admin = adminOpt.get();
            boolean hasRole = admin.getRoles() != null && admin.getRoles().stream()
                    .anyMatch(role -> PLATFORM_ADMIN_ROLE.equals(role.getName()));
            if (!hasRole) {
                Role platformAdmin = findSystemRole(PLATFORM_ADMIN_ROLE);
                if (platformAdmin != null) {
                    userService.assignRoleToUser(admin.getId(), platformAdmin.getId());
                    logger.info("已为用户 admin 分配角色 {}", PLATFORM_ADMIN_ROLE);
                }
            }
        } catch (Exception e) {
            logger.error("为admin用户分配平台管理员角色时发生错误", e);
        }
    }

    /**
     * 在系统角色中按名称查找
     */
    private Role findSystemRole(String name) {
        List<Role> systemRoles = roleService.getSystemRoles();
        return systemRoles == null ? null : systemRoles.stream()
                .filter(role -> name.equals(role.getName()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 内置物流渠道（幂等，按渠道编码跳过已存在渠道）。
     * <p>
     * 平台物流导入按渠道编码/承运商名称定位渠道，渠道缺失会导致导入行整行失败，
     * 因此渠道必须在启动时具备最小可用集合。
     * </p>
     */
    private void seedLogisticsChannels() {
        try {
            int created = 0;
            for (SeedChannel seed : SEED_LOGISTICS_CHANNELS) {
                if (logisticsChannelService.findByChannelCode(seed.channelCode()).isPresent()) {
                    continue;
                }
                logisticsChannelService.createLogisticsChannel(LogisticsChannel.create(seed.channelCode(),
                        seed.channelName(), seed.description(), seed.countries(), seed.basePrice(),
                        seed.pricePerGram(), "CNY", true));
                created++;
            }
            if (created > 0) {
                logger.info("内置物流渠道已创建 {} 个", created);
            }
        } catch (Exception e) {
            logger.error("初始化内置物流渠道时发生错误", e);
        }
    }
}
