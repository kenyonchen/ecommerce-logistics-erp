package site.hexaarch.ecommerce.logistics.application.service.finance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.finance.aggregate.FinancialTransaction;
import site.hexaarch.ecommerce.logistics.domain.finance.service.FinancialTransactionService;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionType;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;
import site.hexaarch.ecommerce.logistics.infrastructure.security.TenantContext;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 财务流水的租户归属口径测试（ISS-118 / R-30）。
 * <p>
 * 金额记录是"落错桶就再也看不见"代价最高的一类：领域服务 {@code FinancialTransactionServiceImpl}
 * 原本对缺省 tenantId 直接兜底成 default-tenant，而 default-tenant 在 tenants 表里没有行，
 * 财务列表按登录租户收敛后这些流水等于人间蒸发。归属决策因此上移到应用层，统一走
 * {@code TenantAccessGuard.resolveWriteTenant}，本测试钉住四种输入的实际落库租户。
 * </p>
 *
 * @author kenyon
 */
@ExtendWith(MockitoExtension.class)
class FinanceApplicationServiceTenantScopeTest {

    private static final String LOGIN_TENANT = "d0861d6f-7d51-4ec5-a733-fda0a0058176";
    private static final String OTHER_TENANT = "tenant-other";

    @Mock
    private FinancialTransactionService financialTransactionService;
    @Mock
    private DomainEventPublisher domainEventPublisher;
    @InjectMocks
    private FinanceApplicationService financeApplicationService;
    @Captor
    private ArgumentCaptor<String> tenantCaptor;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        TenantContext.clearTenantId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clearTenantId();
    }

    private void loginTenantUser() {
        TenantContext.setTenantId(LOGIN_TENANT);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", null, AuthorityUtils.createAuthorityList("ROLE_TENANT_ADMIN")));
    }

    private void loginPlatformAdmin() {
        TenantContext.setTenantId(LOGIN_TENANT);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "root", null, AuthorityUtils.createAuthorityList("ROLE_PLATFORM_ADMIN", "ROLE_TENANT_ADMIN")));
    }

    private void stubCreation() {
        when(financialTransactionService.createFinancialTransaction(
                any(TransactionType.class), any(BigDecimal.class), anyString(),
                anyString(), anyString(), anyString(), any()))
                .thenReturn(mock(FinancialTransaction.class));
    }

    private void create(String tenantId) {
        financeApplicationService.createFinancialTransaction(
                TransactionType.EXPENSE, new BigDecimal("199.00"), "CNY",
                "ORD-1", "ORDER", "冒烟金额", tenantId);
    }

    @Test
    @DisplayName("缺省租户的金额记录归属登录租户而非历史默认桶")
    void createsTransactionForLoggedInTenantWhenTenantIdOmitted() {
        loginTenantUser();
        stubCreation();

        create(null);

        verify(financialTransactionService).createFinancialTransaction(
                eq(TransactionType.EXPENSE), any(BigDecimal.class), eq("CNY"),
                eq("ORD-1"), eq("ORDER"), anyString(), tenantCaptor.capture());
        assertEquals(LOGIN_TENANT, tenantCaptor.getValue());
    }

    @Test
    @DisplayName("六参重载（不传租户）同样归属登录租户")
    void sixArgumentOverloadDoesNotDropIntoLegacyBucket() {
        loginTenantUser();
        stubCreation();

        financeApplicationService.createFinancialTransaction(
                TransactionType.INCOME, new BigDecimal("88.00"), "CNY",
                "ORD-2", "ORDER", "重载入口");

        verify(financialTransactionService).createFinancialTransaction(
                any(TransactionType.class), any(BigDecimal.class), anyString(),
                anyString(), anyString(), anyString(), tenantCaptor.capture());
        assertEquals(LOGIN_TENANT, tenantCaptor.getValue());
    }

    @Test
    @DisplayName("租户用户显式请求他租户建流水被拒")
    void rejectsCrossTenantCreationForTenantUsers() {
        loginTenantUser();

        assertThrows(BusinessException.class, () -> create(OTHER_TENANT));
    }

    @Test
    @DisplayName("平台管理员代客操作可显式指定他租户")
    void allowsPlatformAdminToWriteIntoAnotherTenant() {
        loginPlatformAdmin();
        stubCreation();

        create(OTHER_TENANT);

        verify(financialTransactionService).createFinancialTransaction(
                any(TransactionType.class), any(BigDecimal.class), anyString(),
                anyString(), anyString(), anyString(), tenantCaptor.capture());
        assertEquals(OTHER_TENANT, tenantCaptor.getValue());
    }

    @Test
    @DisplayName("完全没有会话的内部调用才允许落到历史默认桶")
    void fallsBackToLegacyBucketOnlyWithoutSession() {
        stubCreation();

        create(null);

        verify(financialTransactionService).createFinancialTransaction(
                any(TransactionType.class), any(BigDecimal.class), anyString(),
                anyString(), anyString(), anyString(), tenantCaptor.capture());
        assertEquals("default-tenant", tenantCaptor.getValue());
    }
}
