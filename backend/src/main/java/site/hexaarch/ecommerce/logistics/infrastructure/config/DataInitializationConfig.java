package site.hexaarch.ecommerce.logistics.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.application.service.TenantServiceImpl;
import site.hexaarch.ecommerce.logistics.domain.tenant.aggregate.Tenant;
import site.hexaarch.ecommerce.logistics.domain.tenant.entity.User;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.TenantRepository;

/**
 * 数据初始化配置，用于在应用启动时检查并创建默认租户和用户
 *
 * @author kenyon
 */
@Component
public class DataInitializationConfig implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializationConfig.class);

    private final TenantServiceImpl tenantService;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public DataInitializationConfig(TenantServiceImpl tenantService, 
                                  TenantRepository tenantRepository,
                                  PasswordEncoder passwordEncoder) {
        this.tenantService = tenantService;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        initializeDefaultTenantAndUser();
    }

    /**
     * 初始化默认租户和用户
     */
    private void initializeDefaultTenantAndUser() {
        try {
            // 检查是否已存在租户
            var existingTenants = tenantRepository.findAll();
            
            if (existingTenants.isEmpty()) {
                logger.info("未检测到现有租户，开始创建默认租户和管理员用户...");
                
                // 创建默认租户
                Tenant defaultTenant = tenantService.createTenant(
                        "默认租户",
                        "系统默认创建的租户",
                        "admin@example.com",
                        "13800138000"
                );
                
                logger.info("默认租户创建成功，ID: {}", defaultTenant.getId());
                
                // 创建租户管理员用户
                String encodedPassword = passwordEncoder.encode("123456");
                User adminUser = tenantService.createTenantAdmin(
                        defaultTenant.getId(),
                        "admin",
                        encodedPassword,
                        "admin@example.com",
                        "系统",
                        "管理员"
                );
                
                logger.info("默认管理员用户创建成功，用户名: {}", adminUser.getUsername());
                logger.info("默认密码: 123456");
                logger.info("请在生产环境中及时修改默认密码！");
                
            } else {
                logger.info("检测到 {} 个现有租户，跳过默认租户创建", existingTenants.size());
            }
        } catch (Exception e) {
            logger.error("初始化默认租户和用户时发生错误", e);
        }
    }
}