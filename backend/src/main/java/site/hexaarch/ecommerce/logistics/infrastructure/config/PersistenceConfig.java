package site.hexaarch.ecommerce.logistics.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsChannelRepository;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsOrderRepository;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderRepository;
import site.hexaarch.ecommerce.logistics.domain.overview.repository.OverviewStatisticsRepository;
import site.hexaarch.ecommerce.logistics.domain.product.repository.ProductRepository;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.RoleRepository;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.TenantRepository;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.UserRepository;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.InventoryRecordRepository;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.WarehouseRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.logistics.LogisticsChannelMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.logistics.LogisticsOrderMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.order.OrderMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.tenant.RoleMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.tenant.TenantMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.tenant.UserMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.warehouse.WarehouseMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.customer.CustomerJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.finance.FinancialTransactionJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.logistics.LogisticsChannelJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.logistics.LogisticsOrderJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.order.OrderJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.platform.PlatformIntegrationJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.product.ProductJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.purchase.PurchaseOrderJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.tenant.RoleJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.tenant.TenantJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.tenant.UserJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.warehouse.InventoryRecordJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.warehouse.WarehouseJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.logistics.LogisticsChannelRepositoryImpl;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.logistics.LogisticsOrderRepositoryImpl;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.order.OrderRepositoryImpl;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.overview.OverviewStatisticsRepositoryImpl;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.product.ProductRepositoryImpl;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.tenant.RoleRepositoryImpl;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.tenant.TenantRepositoryImpl;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.tenant.UserRepositoryImpl;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.warehouse.InventoryRecordRepositoryImpl;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.warehouse.WarehouseRepositoryImpl;

/**
 * 持久化配置类，注册仓库实现为Spring Bean。
 *
 * @author kenyon
 */
@Configuration
public class PersistenceConfig {
    // 订单仓库配置
    @Bean
    public OrderRepository orderRepository(OrderJpaRepository orderJpaRepository, OrderMapper orderMapper) {
        return new OrderRepositoryImpl(orderJpaRepository, orderMapper);
    }

    // 物流单仓库配置
    @Bean
    public LogisticsOrderRepository logisticsOrderRepository(
            LogisticsOrderJpaRepository logisticsOrderJpaRepository,
            LogisticsOrderMapper logisticsOrderMapper) {
        return new LogisticsOrderRepositoryImpl(logisticsOrderJpaRepository, logisticsOrderMapper);
    }

    // 物流渠道仓库配置
    @Bean
    public LogisticsChannelRepository logisticsChannelRepository(
            LogisticsChannelJpaRepository logisticsChannelJpaRepository,
            LogisticsChannelMapper logisticsChannelMapper) {
        return new LogisticsChannelRepositoryImpl(logisticsChannelJpaRepository, logisticsChannelMapper);
    }

    // 仓库仓库配置
    @Bean
    public WarehouseRepository warehouseRepository(
            WarehouseJpaRepository warehouseJpaRepository,
            WarehouseMapper warehouseMapper) {
        return new WarehouseRepositoryImpl(warehouseJpaRepository, warehouseMapper);
    }

    // 库存记录仓库配置
    @Bean
    public InventoryRecordRepository inventoryRecordRepository(
            InventoryRecordJpaRepository inventoryRecordJpaRepository,
            WarehouseMapper warehouseMapper) {
        return new InventoryRecordRepositoryImpl(inventoryRecordJpaRepository, warehouseMapper);
    }

    // 产品仓库配置
    @Bean
    public ProductRepository productRepository(ProductRepositoryImpl productRepositoryImpl) {
        return productRepositoryImpl;
    }

    // 用户仓库配置
    @Bean
    public UserRepository userRepository(UserJpaRepository userJpaRepository,
                                          RoleJpaRepository roleJpaRepository,
                                          UserMapper userMapper) {
        return new UserRepositoryImpl(userJpaRepository, roleJpaRepository, userMapper);
    }

    // 角色仓库配置
    @Bean
    public RoleRepository roleRepository(RoleJpaRepository roleJpaRepository, RoleMapper roleMapper) {
        return new RoleRepositoryImpl(roleJpaRepository, roleMapper);
    }

    // 租户仓库配置
    @Bean
    public TenantRepository tenantRepository(TenantJpaRepository tenantJpaRepository, TenantMapper tenantMapper) {
        return new TenantRepositoryImpl(tenantJpaRepository, tenantMapper);
    }

    // 系统概览统计仓库配置（跨域聚合读模型）
    @Bean
    public OverviewStatisticsRepository overviewStatisticsRepository(
            OrderJpaRepository orderJpaRepository,
            ProductJpaRepository productJpaRepository,
            WarehouseJpaRepository warehouseJpaRepository,
            CustomerJpaRepository customerJpaRepository,
            PurchaseOrderJpaRepository purchaseOrderJpaRepository,
            PlatformIntegrationJpaRepository platformIntegrationJpaRepository,
            FinancialTransactionJpaRepository financialTransactionJpaRepository,
            OrderMapper orderMapper) {
        return new OverviewStatisticsRepositoryImpl(
                orderJpaRepository,
                productJpaRepository,
                warehouseJpaRepository,
                customerJpaRepository,
                purchaseOrderJpaRepository,
                platformIntegrationJpaRepository,
                financialTransactionJpaRepository,
                orderMapper);
    }
}
