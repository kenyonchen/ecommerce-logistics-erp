package site.hexaarch.ecommerce.logistics.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsChannelRepository;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsOrderRepository;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderRepository;
import site.hexaarch.ecommerce.logistics.domain.product.repository.ProductRepository;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.RoleRepository;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.TenantRepository;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.UserRepository;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.InventoryRecordRepository;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.WarehouseRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.LogisticsChannelMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.LogisticsOrderMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.OrderMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.RoleMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.TenantMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.UserMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.WarehouseMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.InventoryRecordJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.LogisticsChannelJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.LogisticsOrderJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.OrderJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.ProductRepositoryImpl;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.RoleJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.TenantJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.UserJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.WarehouseJpaRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.InventoryRecordRepositoryImpl;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.LogisticsChannelRepositoryImpl;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.LogisticsOrderRepositoryImpl;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.OrderRepositoryImpl;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.RoleRepositoryImpl;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.TenantRepositoryImpl;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.UserRepositoryImpl;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.WarehouseRepositoryImpl;

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
    public UserRepository userRepository(UserJpaRepository userJpaRepository, UserMapper userMapper) {
        return new UserRepositoryImpl(userJpaRepository, userMapper);
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
}
