package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.tenant.TenantMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.tenant.TenantJpaRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code countBusinessRecords} 的口径测试（ISS-119）。
 * <p>
 * 这里刻意不打真实数据库（仓库无 {@code @DataJpaTest} 底座，为一条计数语句引入整套容器不划算），
 * 而是把断言压在它唯一的行为面——<b>逐表生成的那条 SQL 文本</b>上：计数语义完全由
 * {@code WHERE} 子句决定，把墓碑排除条件钉死在 SQL 形状上，就等价于把"租户删不掉"这个缺陷钉死。
 * </p>
 * <p>
 * 存在理由：客户是唯一软删除的聚合，"把租户下的客户删干净"会产生 {@code status='DELETED'} 的墓碑行；
 * 计数若把墓碑算成业务数据，该租户就永远过不了 {@code TenantServiceImpl:133} 的删除前置检查，
 * 而列表与详情都看不见那条行——用户面对的是"我明明删光了，却说不清还有什么"。实测修复前返回
 * 400「仍有 1 条业务数据」，那 1 条正是一条墓碑。
 * </p>
 *
 * @author kenyon
 */
@ExtendWith(MockitoExtension.class)
class TenantRepositoryImplBusinessCountTest {

    private static final String TENANT_ID = "d0861d6f-7d51-4ec5-a733-fda0a0058176";

    /**
     * 与 {@code BUSINESS_TABLES} 同数量：逐表计数，漏一张表就等于给删除检查放行了一类数据。
     */
    private static final int BUSINESS_TABLE_COUNT = 22;

    @Mock
    private TenantJpaRepository tenantJpaRepository;
    @Mock
    private TenantMapper tenantMapper;
    @Mock
    private EntityManager entityManager;

    /**
     * 模拟数据库按 (表名 → COUNT(*) 结果) 回答；键为表名而非整条 SQL，
     * 这样"排除条件写进 SQL"才是决定结果的唯一途径。
     */
    private final Map<String, Long> countsByTable = new HashMap<>();

    private TenantRepositoryImpl repository;

    @BeforeEach
    void wireEntityManager() {
        repository = new TenantRepositoryImpl(tenantJpaRepository, tenantMapper);
        ReflectionTestUtils.setField(repository, "entityManager", entityManager);
        when(entityManager.createNativeQuery(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            Query each = mock(Query.class);
            when(each.setParameter("tenantId", TENANT_ID)).thenReturn(each);
            when(each.getSingleResult()).thenReturn(countsByTable.getOrDefault(tableOf(sql), 0L));
            return each;
        });
    }

    @Test
    @DisplayName("客户计数排除 DELETED 墓碑：软删干净的客户不再挡住租户删除")
    void deletedCustomersAreExcludedFromTheBusinessDataCount() {
        assertEquals(0L, repository.countBusinessRecords(TENANT_ID),
                "库里只剩墓碑时应视为无业务数据（该表按 SQL 的排除条件返回 0）");

        String customersSql = sqlOf(capturedSqls(), "customers");
        assertTrue(customersSql.endsWith(" AND status <> 'DELETED'"),
                () -> "客户计数没有排除墓碑：删除租户会被自己的删除记录永久挡住 -> " + customersSql);
    }

    @Test
    @DisplayName("只有客户表带墓碑排除条件：硬删除聚合不得被状态列悄悄放行")
    void onlyCustomersCarryAnInvisibleRowPredicate() {
        List<String> sqls = capturedSqls();
        List<String> withPredicate = sqls.stream().filter(sql -> sql.contains("status")).toList();

        assertEquals(1, withPredicate.size(), () -> "排除条件扩散到了别的表（那些聚合是硬删除，"
                + "加状态条件会让真实业务数据对删除检查隐身）: " + withPredicate);
        assertEquals("customers", tableOf(withPredicate.get(0)));
    }

    @Test
    @DisplayName("每张表都按租户收敛，且租户 ID 走绑定变量不进 SQL 文本")
    void everyTableQueryIsTenantScopedAndUsesABoundParameter() {
        List<String> sqls = capturedSqls();
        assertEquals(BUSINESS_TABLE_COUNT, sqls.size(),
                () -> "计数覆盖的表数量变了，请同步 BUSINESS_TABLES 与本测试（漏表 = 删除检查放行）");

        for (String sql : sqls) {
            assertTrue(sql.contains("tenant_id = :tenantId"), () -> "该表没有租户条件，会跨租户计数: " + sql);
            assertFalse(sql.contains(TENANT_ID), () -> "租户 ID 被拼进了 SQL 文本（注入面）: " + sql);
        }
    }

    @Test
    @DisplayName("计数是各表可见行之和：任一表有真实数据都不放行删除")
    void totalsSumAcrossEveryVisibleBusinessRow() {
        countsByTable.put("orders", 2L);
        countsByTable.put("customers", 1L);
        countsByTable.put("purchase_orders", 3L);

        assertEquals(6L, repository.countBusinessRecords(TENANT_ID));
    }

    @Test
    @DisplayName("无租户行时不报错，返回 0")
    void emptyTenantReturnsZeroInsteadOfThrowing() {
        assertEquals(0L, repository.countBusinessRecords(TENANT_ID));
    }

    /**
     * 触发一次计数并取回逐表生成的 SQL：本测试的断言面就是这条 SQL 文本。
     */
    private List<String> capturedSqls() {
        repository.countBusinessRecords(TENANT_ID);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(entityManager, atLeastOnce()).createNativeQuery(captor.capture());
        return captor.getAllValues();
    }

    private static String sqlOf(List<String> sqls, String table) {
        return sqls.stream().filter(sql -> tableOf(sql).equals(table)).findFirst()
                .orElseThrow(() -> new AssertionError("没有为 " + table + " 生成计数 SQL: " + sqls));
    }

    /**
     * {@code SELECT COUNT(*) FROM <table> WHERE ...} 的第 4 个 token。
     */
    private static String tableOf(String sql) {
        return sql.split(" ")[3];
    }
}
