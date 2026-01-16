package site.hexaarch.ecommerce.logistics.domain.logistics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.logistics.aggregate.LogisticsOrder;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsOrderRepository;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsStatus;

/**
 * 物流单处理服务，负责处理物流单的创建、修改等操作。
 *
 * @author kenyon
 */
@Service
@RequiredArgsConstructor
public class LogisticsOrderProcessingService {
    private final LogisticsOrderRepository logisticsOrderRepository;
    private final LogisticsChannelService logisticsChannelService;
    private final LogisticsTrackingService logisticsTrackingService;

    /**
     * 创建物流单。
     *
     * @param tenantId           租户ID
     * @param orderId            订单ID
     * @param logisticsChannelId 物流渠道ID
     * @return 创建的物流单
     */
    public LogisticsOrder createLogisticsOrder(String tenantId, String orderId, String logisticsChannelId) {
        // 验证物流渠道是否存在
        logisticsChannelService.findById(logisticsChannelId)
                .orElseThrow(() -> new IllegalArgumentException("Logistics channel not found: " + logisticsChannelId));

        // 创建物流单
        LogisticsOrder logisticsOrder = LogisticsOrder.create(tenantId, orderId, logisticsChannelId);

        // 保存物流单
        return logisticsOrderRepository.save(logisticsOrder);
    }

    /**
     * 更新物流单状态。
     *
     * @param logisticsOrderId 物流单ID
     * @param newStatus        新的物流状态
     * @return 更新后的物流单
     */
    public LogisticsOrder updateLogisticsStatus(String logisticsOrderId, LogisticsStatus newStatus) {
        // 查找物流单
        LogisticsOrder logisticsOrder = logisticsOrderRepository.findById(logisticsOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Logistics order not found: " + logisticsOrderId));

        // 更新物流状态
        logisticsOrder.updateStatus(newStatus);

        // 保存物流单
        return logisticsOrderRepository.save(logisticsOrder);
    }

    /**
     * 处理物流单。
     *
     * @param logisticsOrderId 物流单ID
     * @return 处理后的物流单
     */
    public LogisticsOrder processLogisticsOrder(String logisticsOrderId) {
        // 查找物流单
        LogisticsOrder logisticsOrder = logisticsOrderRepository.findById(logisticsOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Logistics order not found: " + logisticsOrderId));

        // 更新物流状态为已揽收
        logisticsOrder.updateStatus(LogisticsStatus.COLLECTED);

        // 保存物流单
        return logisticsOrderRepository.save(logisticsOrder);
    }

    /**
     * 取消物流单。
     *
     * @param logisticsOrderId 物流单ID
     * @return 取消后的物流单
     */
    public LogisticsOrder cancelLogisticsOrder(String logisticsOrderId) {
        // 查找物流单
        LogisticsOrder logisticsOrder = logisticsOrderRepository.findById(logisticsOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Logistics order not found: " + logisticsOrderId));

        // 检查物流状态是否可以取消
        if (logisticsOrder.getLogisticsStatus().isCollected() || logisticsOrder.getLogisticsStatus().isInTransit() || logisticsOrder.getLogisticsStatus().isDelivered()) {
            throw new IllegalStateException("Cannot cancel logistics order that has been collected, in transit, or delivered");
        }

        // 更新物流状态为已取消
        logisticsOrder.updateStatus(LogisticsStatus.CANCELLED);

        // 保存物流单
        return logisticsOrderRepository.save(logisticsOrder);
    }

    /**
     * 生成物流标签。
     *
     * @param logisticsOrder 物流单
     */
    public void generateLogisticsLabel(LogisticsOrder logisticsOrder) {
        // 这里可以添加生成物流标签的逻辑
        // 简化实现，只更新状态
        logisticsOrder.updateStatus(LogisticsStatus.LABEL_GENERATED);
    }
}