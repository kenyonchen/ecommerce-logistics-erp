package site.hexaarch.ecommerce.logistics.application.service.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.annotation.Transactional;
import site.hexaarch.ecommerce.logistics.domain.order.aggregate.Order;
import site.hexaarch.ecommerce.logistics.domain.order.aggregate.OrderImportJob;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderFieldMappingRepository;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderImportJobRepository;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderRepository;
import site.hexaarch.ecommerce.logistics.domain.order.service.OrderFieldMappingService;
import site.hexaarch.ecommerce.logistics.domain.platform.repository.PlatformIntegrationRepository;
import site.hexaarch.ecommerce.logistics.domain.product.valueobject.ImportJobStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.importing.ImportSourceParser;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformFetcherRegistry;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth.PlatformCredentials;
import site.hexaarch.ecommerce.logistics.interfaces.dto.orderimport.OrderImportItemDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.orderimport.OrderImportJobDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.orderimport.OrderImportRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.orderimport.OrderImportRowDto;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单导入应用服务持久化形状测试（ISS-067 / ISS-071）：
 * 任务只在 complete() 之后单次持久化，落库时必为终态且错误明细完整；导入入口带事务注解。
 */
class OrderImportApplicationServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderFieldMappingRepository orderFieldMappingRepository;
    @Mock
    private OrderImportJobRepository orderImportJobRepository;
    @Mock
    private PlatformIntegrationRepository platformIntegrationRepository;
    @Mock
    private DomainEventPublisher domainEventPublisher;

    private OrderImportApplicationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new OrderImportApplicationService(
                orderRepository,
                new OrderFieldMappingService(orderFieldMappingRepository),
                orderFieldMappingRepository,
                orderImportJobRepository,
                platformIntegrationRepository,
                new ImportSourceParser(),
                domainEventPublisher,
                new PlatformFetcherRegistry(List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of()),
                new PlatformCredentials(platformIntegrationRepository, List.of(), Duration.ofMinutes(5)));
        when(orderImportJobRepository.save(any(OrderImportJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.findByPlatformOrderId(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void shouldPersistJobOnceInTerminalStateWithErrorDetailRows() {
        OrderImportRequest request = new OrderImportRequest();
        request.setTenantId("default-tenant");
        request.setPlatform("AMAZON");
        request.setRows(List.of(validRow(), rowWithoutExternalId()));

        OrderImportJobDto result = service.importOrders(request);

        ArgumentCaptor<OrderImportJob> captor = ArgumentCaptor.forClass(OrderImportJob.class);
        verify(orderImportJobRepository, times(1)).save(captor.capture());
        OrderImportJob persisted = captor.getValue();
        assertEquals(result.getJobId(), persisted.getId());
        assertEquals(ImportJobStatus.PARTIAL_SUCCESS, persisted.getStatus());
        assertEquals(2, persisted.getTotalCount());
        assertEquals(1, persisted.getSuccessCount());
        assertEquals(1, persisted.getFailureCount());
        assertEquals(1, persisted.getErrors().size());
        assertNotNull(persisted.getFinishedAt());
    }

    @Test
    void jobMustNotBePersistedWhileRunning() {
        when(orderRepository.save(any(Order.class))).thenThrow(new RuntimeException("模拟数据库故障"));

        OrderImportRequest request = new OrderImportRequest();
        request.setTenantId("default-tenant");
        request.setPlatform("FILE");
        request.setRows(List.of(validRow()));

        service.importOrders(request);

        ArgumentCaptor<OrderImportJob> captor = ArgumentCaptor.forClass(OrderImportJob.class);
        verify(orderImportJobRepository, times(1)).save(captor.capture());
        OrderImportJob persisted = captor.getValue();
        assertEquals(ImportJobStatus.FAILED, persisted.getStatus());
        assertEquals(1, persisted.getFailureCount());
        assertEquals("模拟数据库故障", persisted.getErrors().get(0).getMessage());
    }

    @Test
    void importEntryPointsShouldBeTransactional() throws NoSuchMethodException {
        assertNotNull(OrderImportApplicationService.class
                .getMethod("importOrders", OrderImportRequest.class)
                .getAnnotation(Transactional.class), "importOrders 必须声明 @Transactional（ISS-071）");
        assertNotNull(OrderImportApplicationService.class
                .getMethod("syncFromPlatform", String.class, String.class, String.class, String.class,
                        String.class, Integer.class, Boolean.class)
                .getAnnotation(Transactional.class), "syncFromPlatform 必须声明 @Transactional（自调用绕过代理，需同标）");
    }

    private static OrderImportRowDto validRow() {
        OrderImportRowDto row = new OrderImportRowDto();
        row.setRowNumber(1);
        row.setExternalOrderId("AMZ-ORD-001");
        row.setOrderNumber("ORD-001");
        row.setCustomerName("张三");
        OrderImportItemDto item = new OrderImportItemDto();
        item.setSku("SKU-001");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("99.50"));
        row.setItems(List.of(item));
        return row;
    }

    private static OrderImportRowDto rowWithoutExternalId() {
        OrderImportRowDto row = new OrderImportRowDto();
        row.setRowNumber(2);
        row.setCustomerName("李四");
        return row;
    }
}
