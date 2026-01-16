package site.hexaarch.ecommerce.logistics.domain.logistics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.logistics.aggregate.LogisticsOrder;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsOrderRepository;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsStatus;

/**
 * 物流跟踪服务，负责跟踪物流状态和更新物流信息。
 *
 * @author kenyon
 */
@Service
@RequiredArgsConstructor
public class LogisticsTrackingService {
    private final LogisticsOrderRepository logisticsOrderRepository;

    /**
     * 跟踪物流状态。
     *
     * @param logisticsOrderId 物流单ID
     * @return 物流状态
     */
    public LogisticsStatus trackLogisticsStatus(String logisticsOrderId) {
        // 查找物流单
        LogisticsOrder logisticsOrder = logisticsOrderRepository.findById(logisticsOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Logistics order not found: " + logisticsOrderId));

        // 这里应该调用物流渠道的API获取最新的物流状态
        // 简化处理，直接返回当前状态
        return logisticsOrder.getLogisticsStatus();
    }

    /**
     * 更新物流状态。
     *
     * @param logisticsOrderId 物流单ID
     * @param logisticsStatus  物流状态
     * @return 更新后的物流单
     */
    public LogisticsOrder updateLogisticsStatus(String logisticsOrderId, LogisticsStatus logisticsStatus) {
        // 查找物流单
        LogisticsOrder logisticsOrder = logisticsOrderRepository.findById(logisticsOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Logistics order not found: " + logisticsOrderId));

        // 更新物流状态
        logisticsOrder.updateStatus(logisticsStatus);

        // 保存物流单
        return logisticsOrderRepository.save(logisticsOrder);
    }
}