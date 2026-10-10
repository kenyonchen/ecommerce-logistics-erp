package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.domain.tenant.aggregate.Tenant;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.TenantRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.tenant.TenantMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.tenant.TenantJpaRepository;

/**
 * TenantRepository 实现类，用于操作租户聚合根的持久化操作。
 *
 * @author kenyon
 */
@Repository
public class TenantRepositoryImpl implements TenantRepository {

    private final TenantJpaRepository tenantJpaRepository;
    private final TenantMapper tenantMapper;

    @PersistenceContext
    private EntityManager entityManager;

    public TenantRepositoryImpl(TenantJpaRepository tenantJpaRepository, TenantMapper tenantMapper) {
        this.tenantJpaRepository = tenantJpaRepository;
        this.tenantMapper = tenantMapper;
    }

    @Override
    public Tenant save(Tenant tenant) {
        var jpaEntity = tenantMapper.toJpaEntity(tenant);
        var savedEntity = tenantJpaRepository.save(jpaEntity);
        return tenantMapper.toDomainEntity(savedEntity);
    }

    @Override
    public Optional<Tenant> findById(String id) {
        return tenantJpaRepository.findById(id)
                .map(tenantMapper::toDomainEntity);
    }

    @Override
    public Optional<Tenant> findByName(String name) {
        return tenantJpaRepository.findByName(name)
                .map(tenantMapper::toDomainEntity);
    }

    @Override
    public Optional<Tenant> findByContactEmail(String email) {
        return tenantJpaRepository.findByContactEmail(email)
                .map(tenantMapper::toDomainEntity);
    }

    @Override
    public List<Tenant> findAll() {
        return tenantJpaRepository.findAll()
                .stream()
                .map(tenantMapper::toDomainEntity)
                .toList();
    }

    @Override
    public void deleteById(String id) {
        tenantJpaRepository.deleteById(id);
    }

    /**
     * 租户下仍算「有业务数据」的表。
     * <p>
     * 由 {@code information_schema} 实测得出（25 张带 tenant_id 的表，
     * 扣除身份域 users/roles 与合规留痕 audit_logs），是常量不是入参，无注入面。
     * </p>
     */
    private static final List<String> BUSINESS_TABLES = List.of(
            "orders", "products", "customers", "warehouses",
            "shops", "shop_warehouses", "logistics_orders", "purchase_orders", "financial_transactions",
            "platform_integrations",
            "order_import_jobs", "product_import_jobs", "customer_import_jobs",
            "logistics_import_jobs", "finance_import_jobs", "shop_warehouse_import_jobs",
            "order_field_mappings", "product_field_mappings", "customer_field_mappings",
            "logistics_field_mappings", "finance_field_mappings", "shop_warehouse_field_mappings");

    /**
     * 各表「不再算业务数据」的排除条件（常量，不接外参，无注入面）。
     * <p>
     * 客户是唯一采用软删除的聚合：{@code DELETE /api/customers/{id}} 只把 {@code status} 置成
     * {@code DELETED}，行留在库里，而列表与详情都不再显示它。若计数把这类墓碑也算成"仍有业务数据"，
     * 那么"把租户下的客户删干净"这个动作本身就让该租户永远删不掉（实测 400「仍有 1 条业务数据」，
     * 而那 1 条正是一条看不见的墓碑）。其余聚合（商品、店铺、订单）走硬删除，不存在墓碑。
     * </p>
     */
    private static final Map<String, String> INVISIBLE_ROW_PREDICATES = Map.of(
            "customers", " AND status <> 'DELETED'");

    @Override
    public long countBusinessRecords(String tenantId) {
        long total = 0;
        for (String table : BUSINESS_TABLES) {
            // 表名与排除条件都来自上面的常量，租户ID走绑定变量，无拼接注入面
            String predicate = INVISIBLE_ROW_PREDICATES.getOrDefault(table, "");
            Object result = entityManager.createNativeQuery(
                            "SELECT COUNT(*) FROM " + table + " WHERE tenant_id = :tenantId" + predicate)
                    .setParameter("tenantId", tenantId)
                    .getSingleResult();
            total += ((Number) result).longValue();
        }
        return total;
    }
}