package site.hexaarch.ecommerce.logistics.application.service;

import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.logistics.aggregate.LogisticsOrder;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsChannelRepository;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsOrderRepository;
import site.hexaarch.ecommerce.logistics.domain.logistics.service.LogisticsOrderProcessingService;
import site.hexaarch.ecommerce.logistics.domain.logistics.service.LogisticsTrackingService;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsStatus;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;

import java.util.List;

/**
 * 物流应用服务，协调领域对象完成物流相关的业务操作。
 *
 * @author kenyon
 */
@Service
public class LogisticsApplicationService {
    private final LogisticsOrderRepository logisticsOrderRepository;
    private final LogisticsChannelRepository logisticsChannelRepository;
    private final OrderRepository orderRepository;
    private final LogisticsOrderProcessingService logisticsOrderProcessingService;
    private final LogisticsTrackingService logisticsTrackingService;
    private final DomainEventPublisher domainEventPublisher;

    // 手动添加构造函数，避免Lombok注解问题
    public LogisticsApplicationService(LogisticsOrderRepository logisticsOrderRepository, LogisticsChannelRepository logisticsChannelRepository, OrderRepository orderRepository, LogisticsOrderProcessingService logisticsOrderProcessingService, LogisticsTrackingService logisticsTrackingService, DomainEventPublisher domainEventPublisher) {
        this.logisticsOrderRepository = logisticsOrderRepository;
        this.logisticsChannelRepository = logisticsChannelRepository;
        this.orderRepository = orderRepository;
        this.logisticsOrderProcessingService = logisticsOrderProcessingService;
        this.logisticsTrackingService = logisticsTrackingService;
        this.domainEventPublisher = domainEventPublisher;
    }

    /**
     * 创建物流单
     */
    public LogisticsOrder createLogisticsOrder(String tenantId, String orderId, String channelCode) {
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        // 简化实现，使用builder创建LogisticsOrder
        LogisticsOrder logisticsOrder = LogisticsOrder.builder()
                .tenantId(tenantId)
                .orderId(orderId)
                .logisticsChannelId(channelCode)
                .build();

        LogisticsOrder savedLogisticsOrder = logisticsOrderRepository.save(logisticsOrder);
        // 发布物流单聚合中的所有领域事件
        domainEventPublisher.publishEventsFrom(savedLogisticsOrder);
        return savedLogisticsOrder;
    }

    /**
     * 更新物流状态
     */
    public LogisticsOrder updateLogisticsStatus(String logisticsOrderId, LogisticsStatus newStatus) {
        var logisticsOrder = logisticsOrderRepository.findById(logisticsOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Logistics order not found"));

        logisticsOrder.updateStatus(newStatus);
        LogisticsOrder savedLogisticsOrder = logisticsOrderRepository.save(logisticsOrder);
        // 发布物流单聚合中的所有领域事件
        domainEventPublisher.publishEventsFrom(savedLogisticsOrder);
        return savedLogisticsOrder;
    }

    /**
     * 生成物流标签
     */
    public LogisticsOrder generateLogisticsLabel(String logisticsOrderId) {
        var logisticsOrder = logisticsOrderRepository.findById(logisticsOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Logistics order not found"));

        logisticsOrderProcessingService.generateLogisticsLabel(logisticsOrder);
        return logisticsOrderRepository.save(logisticsOrder);
    }

    /**
     * 跟踪物流状态
     */
    public LogisticsOrder trackLogisticsOrder(String logisticsOrderId) {
        var logisticsOrder = logisticsOrderRepository.findById(logisticsOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Logistics order not found"));

        // 调用trackLogisticsStatus获取最新状态，然后更新物流单
        LogisticsStatus latestStatus = logisticsTrackingService.trackLogisticsStatus(logisticsOrderId);
        logisticsOrder.updateStatus(latestStatus);
        return logisticsOrderRepository.save(logisticsOrder);
    }

    /**
     * 查找物流单
     */
    public LogisticsOrder findLogisticsOrderById(String logisticsOrderId) {
        return logisticsOrderRepository.findById(logisticsOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Logistics order not found"));
    }

    /**
     * 根据订单ID查找物流单
     */
    public List<LogisticsOrder> findLogisticsOrderByOrderId(String orderId) {
        return logisticsOrderRepository.findByOrderId(orderId);
    }

    /**
     * 查找所有物流单
     */
    public List<LogisticsOrder> findAllLogisticsOrders() {
        return logisticsOrderRepository.findAll();
    }
}
