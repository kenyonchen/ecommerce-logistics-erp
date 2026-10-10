package site.hexaarch.ecommerce.logistics.application.service.customer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import site.hexaarch.ecommerce.logistics.domain.customer.service.CustomerService;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;
import site.hexaarch.ecommerce.logistics.infrastructure.security.TenantContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * 创建客户的租户归属测试（ISS-118）：带会话但未传 tenantId 时必须归属登录租户，
 * 不能回退到 tenants 表里根本不存在的 default-tenant 桶。
 *
 * @author kenyon
 */
@ExtendWith(MockitoExtension.class)
class CustomerApplicationServiceTenantScopeTest {

    private static final String LOGIN_TENANT = "d0861d6f-7d51-4ec5-a733-fda0a0058176";
    private static final String LEGACY_BUCKET = "default-tenant";

    @Mock
    private CustomerService customerService;
    @Mock
    private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private CustomerApplicationService customerApplicationService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(LOGIN_TENANT);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", null, AuthorityUtils.createAuthorityList("ROLE_TENANT_ADMIN")));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clearTenantId();
        SecurityContextHolder.clearContext();
    }

    private String capturedTenantForCreate() {
        ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class);
        verify(customerService).createCustomer(tenantCaptor.capture(), any(), any(), any(), any(), any());
        return tenantCaptor.getValue();
    }

    @Test
    void 未传租户时归属登录租户而非历史默认桶() {
        customerApplicationService.createCustomer(null, "张三", "z@example.com", "13800000000", null, null);

        assertEquals(LOGIN_TENANT, capturedTenantForCreate());
    }

    @Test
    void 空白租户串同样收敛到登录租户() {
        customerApplicationService.createCustomer("   ", "李四", "l@example.com", "13800000001", null, null);

        assertEquals(LOGIN_TENANT, capturedTenantForCreate());
    }

    @Test
    void 显式传登录租户时原样使用并去掉首尾空格() {
        customerApplicationService.createCustomer(" " + LOGIN_TENANT + " ", "王五", "w@example.com",
                "13800000002", null, null);

        assertEquals(LOGIN_TENANT, capturedTenantForCreate());
    }

    @Test
    void 平台导入建客户也走同一口径() {
        customerApplicationService.createCustomerFromPlatform(null, "赵六", "z6@example.com", "13800000003",
                null, null, "SHOPIFY", "buyer-1");

        ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class);
        verify(customerService).createCustomerFromPlatform(tenantCaptor.capture(), any(), any(), any(), any(),
                any(), any(), eq("buyer-1"));
        assertEquals(LOGIN_TENANT, tenantCaptor.getValue());
    }

    @Test
    void 无会话的内部调用才允许落到历史默认桶() {
        TenantContext.clearTenantId();
        SecurityContextHolder.clearContext();

        customerApplicationService.createCustomer(null, "内部", "i@example.com", "13800000004", null, null);

        assertEquals(LEGACY_BUCKET, capturedTenantForCreate());
    }
}
