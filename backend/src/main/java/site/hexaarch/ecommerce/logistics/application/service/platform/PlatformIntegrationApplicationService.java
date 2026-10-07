package site.hexaarch.ecommerce.logistics.application.service.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.application.service.customer.CustomerImportApplicationService;
import site.hexaarch.ecommerce.logistics.application.service.logistics.LogisticsImportApplicationService;
import site.hexaarch.ecommerce.logistics.application.service.order.OrderImportApplicationService;
import site.hexaarch.ecommerce.logistics.application.service.product.ProductImportApplicationService;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.logistics.aggregate.LogisticsOrder;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsOrderRepository;
import site.hexaarch.ecommerce.logistics.domain.order.aggregate.Order;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderRepository;
import site.hexaarch.ecommerce.logistics.domain.platform.aggregate.PlatformIntegration;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformConnectionTestResult;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformFulfillmentPusher.PlatformFulfillmentRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformFulfillmentPusher.PlatformFulfillmentResult;
import site.hexaarch.ecommerce.logistics.domain.platform.service.PlatformIntegrationService;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformFetcherRegistry;
import site.hexaarch.ecommerce.logistics.interfaces.dto.customerimport.CustomerImportJobDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logisticsimport.LogisticsImportJobDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.orderimport.OrderImportJobDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.ProductImportJobDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.ProductImportRequest;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth.PlatformCredentials;

import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台集成应用服务
 *
 * @author kenyon
 */
@Service
public class PlatformIntegrationApplicationService {

    private static final Logger log = LoggerFactory.getLogger(PlatformIntegrationApplicationService.class);

    private final PlatformIntegrationService platformIntegrationService;
    private final ProductImportApplicationService productImportApplicationService;
    private final OrderImportApplicationService orderImportApplicationService;
    private final CustomerImportApplicationService customerImportApplicationService;
    private final LogisticsImportApplicationService logisticsImportApplicationService;
    private final site.hexaarch.ecommerce.logistics.application.service.finance.FinanceImportApplicationService financeImportApplicationService;
    private final DomainEventPublisher domainEventPublisher;
    private final PlatformFetcherRegistry fetcherRegistry;
    private final OrderRepository orderRepository;
    private final LogisticsOrderRepository logisticsOrderRepository;
    private final PlatformCredentials platformCredentials;

    @Autowired
    public PlatformIntegrationApplicationService(PlatformIntegrationService platformIntegrationService,
                                                 ProductImportApplicationService productImportApplicationService,
                                                 OrderImportApplicationService orderImportApplicationService,
                                                 CustomerImportApplicationService customerImportApplicationService,
                                                 LogisticsImportApplicationService logisticsImportApplicationService,
                                                 site.hexaarch.ecommerce.logistics.application.service.finance.FinanceImportApplicationService financeImportApplicationService,
                                                 DomainEventPublisher domainEventPublisher,
                                                 PlatformFetcherRegistry fetcherRegistry,
                                                 OrderRepository orderRepository,
                                                 LogisticsOrderRepository logisticsOrderRepository,
                                                 PlatformCredentials platformCredentials) {
        this.platformIntegrationService = platformIntegrationService;
        this.productImportApplicationService = productImportApplicationService;
        this.orderImportApplicationService = orderImportApplicationService;
        this.customerImportApplicationService = customerImportApplicationService;
        this.logisticsImportApplicationService = logisticsImportApplicationService;
        this.financeImportApplicationService = financeImportApplicationService;
        this.domainEventPublisher = domainEventPublisher;
        this.fetcherRegistry = fetcherRegistry;
        this.orderRepository = orderRepository;
        this.logisticsOrderRepository = logisticsOrderRepository;
        this.platformCredentials = platformCredentials;
    }

    @Transactional
    public PlatformIntegration createPlatformIntegration(PlatformIntegration platformIntegration) {
        PlatformIntegration integration = platformIntegrationService.createPlatformIntegration(platformIntegration);
        // 发布平台集成聚合中的所有领域事件
        domainEventPublisher.publishEventsFrom(integration);
        return integration;
    }

    public PlatformIntegration getPlatformIntegrationById(UUID id) {
        return platformIntegrationService.getPlatformIntegrationById(id);
    }

    public List<PlatformIntegration> getAllPlatformIntegrations() {
        return platformIntegrationService.getAllPlatformIntegrations();
    }

    /**
     * 按租户查询平台集成列表。
     * <p>
     * 形参名为 {@code tenantId}，租户隔离切面会校验其与登录租户一致。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 该租户下的平台集成
     */
    public List<PlatformIntegration> getPlatformIntegrationsByTenant(String tenantId) {
        return platformIntegrationService.getPlatformIntegrationsByTenant(tenantId);
    }

    @Transactional
    public PlatformIntegration updatePlatformIntegration(UUID id, PlatformIntegration platformIntegration) {
        return platformIntegrationService.updatePlatformIntegration(id, platformIntegration);
    }

    @Transactional
    public void deletePlatformIntegration(UUID id) {
        platformIntegrationService.deletePlatformIntegration(id);
    }

    @Transactional
    public PlatformIntegration activatePlatformIntegration(UUID id) {
        return platformIntegrationService.activatePlatformIntegration(id);
    }

    @Transactional
    public PlatformIntegration deactivatePlatformIntegration(UUID id) {
        return platformIntegrationService.deactivatePlatformIntegration(id);
    }

    /**
     * 同步订单：调用订单导入服务完成「拉取 -> 字段映射 -> 批量导入 -> 结果反馈」，
     * 并返回本次导入任务的结果明细。
     */
    public OrderImportJobDto syncOrdersFromPlatform(UUID platformId, String tenantId, String orderNumber,
                                                    String updatedFrom, String updatedTo, Integer limit,
                                                    Boolean useSampleData) {
        OrderImportJobDto result = orderImportApplicationService.syncFromPlatform(String.valueOf(platformId),
                tenantId, orderNumber, updatedFrom, updatedTo, limit, useSampleData);
        try {
            platformIntegrationService.markOrdersSynced(platformId);
        } catch (Exception e) {
            // 同步结果已产生，平台状态更新失败不覆盖导入结果
            log.warn("[PlatformIntegration] 更新最后订单同步时间失败: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 同步客户（买家）：调用客户导入服务完成「拉取 -> 字段映射 -> 批量导入 -> 结果反馈」，
     * 支持从平台买家接口或从订单数据萃取买家两种来源。
     */
    public CustomerImportJobDto syncCustomersFromPlatform(UUID platformId, String tenantId, String sourceType,
                                                           String keyword, Integer limit, Boolean useSampleData) {
        return customerImportApplicationService.syncFromPlatform(String.valueOf(platformId), tenantId,
                sourceType, keyword, limit, useSampleData);
    }

    /**
     * 同步商品：调用商品导入服务完成「拉取 -> 字段映射 -> 批量导入 -> 结果反馈」，
     * 并返回本次导入任务的结果明细。
     */
    public ProductImportJobDto syncProductsFromPlatform(UUID platformId, String tenantId) {
        ProductImportRequest request = new ProductImportRequest();
        request.setIntegrationId(String.valueOf(platformId));
        request.setTenantId(tenantId);
        request.setUpdateExisting(Boolean.TRUE);
        request.setEnableIdempotency(Boolean.TRUE);
        ProductImportJobDto result = productImportApplicationService.importProducts(request);
        try {
            platformIntegrationService.markProductsSynced(platformId);
        } catch (Exception e) {
            // 同步结果已产生，平台状态更新失败不覆盖导入结果
            log.warn("[PlatformIntegration] 更新最后商品同步时间失败: {}", e.getMessage());
        }
        return result;
    }

    public site.hexaarch.ecommerce.logistics.domain.platform.service.InventorySyncResult syncInventoryToPlatform(UUID platformId) {
        return platformIntegrationService.syncInventoryToPlatform(platformId);
    }

    /**
     * 同步物流：调用物流导入服务完成「拉取 -> 字段映射 -> 批量导入 -> 结果反馈」，
     * 物流单按平台物流单号幂等关联，终态物流单不会被回退覆盖。
     */
    public LogisticsImportJobDto syncLogisticsFromPlatform(UUID platformId, String tenantId, String orderNumber,
                                                            String trackingNumber, String shippedFrom,
                                                            String shippedTo, Integer limit, Boolean useSampleData) {
        return logisticsImportApplicationService.syncFromPlatform(String.valueOf(platformId), tenantId, orderNumber,
                trackingNumber, shippedFrom, shippedTo, limit, useSampleData);
    }

    /**
     * 同步财务：调用财务导入服务完成「拉取 -> 字段映射 -> 批量导入 -> 结果反馈」，
     * 财务流水按 (platform, externalTransactionId) 幂等关联。
     */
    public site.hexaarch.ecommerce.logistics.interfaces.dto.financeimport.FinanceImportJobDto
    syncFinanceFromPlatform(UUID platformId, String tenantId, String externalTransactionId,
                            String updatedFrom, String updatedTo, Integer limit, Boolean useSampleData) {
        return financeImportApplicationService.syncFromPlatform(String.valueOf(platformId), tenantId,
                externalTransactionId, updatedFrom, updatedTo, limit, useSampleData);
    }

    /**
     * 连通性测试（BR-04）：按集成记录的认证协议取对应适配器，用一次最小代价只读调用验证凭据与地址。
     * <p>
     * 失败作为结果返回而不是抛异常：测试不通过是预期分支，需要把原因回显给运维。
     */
    public PlatformConnectionTestResult testConnection(UUID platformId) {
        PlatformIntegration integration = platformIntegrationService.getPlatformIntegrationById(platformId);
        PlatformCredential credential = platformCredentials.resolve(integration);
        PlatformConnectionTestResult result = fetcherRegistry.connectionTester(credential).test(credential);
        log.info("[PlatformIntegration] 连通性测试 platformId={} success={} adapter={} latencyMs={}",
                platformId, result.success(), result.adapterType(), result.latencyMs());
        return result;
    }

    /**
     * 发货回写（BR-07 反向链路）：把本地已发货物流单的运单号回写到平台订单。
     * <p>
     * 幂等由物流单上的 {@code externalLogisticsId} 把关——回写成功后记下平台发货单号，
     * 再次调用直接拒绝，而不是让平台生成第二张发货单。
     *
     * @param notifyCustomer 是否让平台给买家发货通知邮件
     */
    @Transactional
    public PlatformFulfillmentResult pushFulfillmentToPlatform(UUID platformId, String logisticsOrderId,
                                                              boolean notifyCustomer) {
        PlatformIntegration integration = platformIntegrationService.getPlatformIntegrationById(platformId);
        LogisticsOrder shipment = logisticsOrderRepository.findById(logisticsOrderId)
                .orElseThrow(() -> new EntityNotFoundException("物流单不存在: " + logisticsOrderId));
        if (shipment.getShippedAt() == null) {
            throw new ValidationException("物流单尚未标记发货，无法回写平台");
        }
        if (isBlank(shipment.getTrackingNumber())) {
            throw new ValidationException("物流单尚未绑定运单号，无法回写平台");
        }
        if (!isBlank(shipment.getExternalLogisticsId())) {
            throw new ValidationException("该物流单已回写过平台发货（平台发货单 " + shipment.getExternalLogisticsId()
                    + "），如需重发请先清除平台关联");
        }
        Order order = orderRepository.findById(shipment.getOrderId())
                .orElseThrow(() -> new EntityNotFoundException("物流单关联的订单不存在: " + shipment.getOrderId()));
        String platformName = integration.getPlatformName() == null ? null : integration.getPlatformName().name();
        if (isBlank(order.getPlatformOrderId())) {
            throw new ValidationException("订单 " + order.getOrderNumber()
                    + " 没有平台订单号(platformOrderId)，不是平台同步来的订单，无法回写发货");
        }
        if (platformName == null || !platformName.equalsIgnoreCase(order.getPlatform())) {
            throw new ValidationException("订单来源平台 " + order.getPlatform()
                    + " 与所选平台集成 " + platformName + " 不一致，请改选正确的集成记录");
        }

        PlatformCredential credential = platformCredentials.resolve(integration);
        PlatformFulfillmentRequest request = new PlatformFulfillmentRequest(order.getPlatformOrderId(),
                shipment.getTrackingNumber(), shipment.getCarrier(), shipment.getTrackingUrl(), notifyCustomer);
        PlatformFulfillmentResult result = fetcherRegistry.fulfillmentPusher(credential).push(request, credential);

        shipment.bindPlatformSource(platformName, result.platformFulfillmentId());
        logisticsOrderRepository.save(shipment);
        log.info("[PlatformIntegration] 发货回写成功 platformId={} logisticsOrderId={} platformOrderId={} fulfillmentId={}",
                platformId, logisticsOrderId, order.getPlatformOrderId(), result.platformFulfillmentId());
        return result;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
