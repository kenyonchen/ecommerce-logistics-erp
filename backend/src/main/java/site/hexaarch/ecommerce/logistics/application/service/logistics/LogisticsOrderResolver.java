package site.hexaarch.ecommerce.logistics.application.service.logistics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.logistics.entity.LogisticsChannel;
import site.hexaarch.ecommerce.logistics.domain.logistics.service.LogisticsChannelService;
import site.hexaarch.ecommerce.logistics.domain.order.aggregate.Order;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderRepository;

/**
 * 物流单相关的订单与渠道定位器。
 * <p>
 * 手工创建、批量发货、平台导入三条链路都需要把「订单标识」与「渠道标识」解析为系统内实体，
 * 统一在此处收敛，避免各应用服务重复实现导致口径不一致。
 * </p>
 *
 * @author kenyon
 */
@Slf4j
@Service
public class LogisticsOrderResolver {

    private final OrderRepository orderRepository;
    private final LogisticsChannelService logisticsChannelService;

    public LogisticsOrderResolver(OrderRepository orderRepository, LogisticsChannelService logisticsChannelService) {
        this.orderRepository = orderRepository;
        this.logisticsChannelService = logisticsChannelService;
    }

    /**
     * 按 orderId / orderNumber / 平台订单号 依次定位订单。
     *
     * @throws EntityNotFoundException 订单不存在
     */
    public Order resolveOrder(String tenantId, String orderId, String orderNumber, String externalOrderId,
                              String platform) {
        if (StringUtils.hasText(orderId)) {
            return orderRepository.findById(orderId.trim())
                    .orElseThrow(() -> new EntityNotFoundException("订单不存在，orderId=" + orderId));
        }
        if (StringUtils.hasText(orderNumber)) {
            return orderRepository.findByOrderNumber(orderNumber.trim())
                    .orElseThrow(() -> new EntityNotFoundException("订单不存在，订单号=" + orderNumber));
        }
        if (StringUtils.hasText(externalOrderId)) {
            String resolvedPlatform = StringUtils.hasText(platform) ? platform.trim().toUpperCase() : null;
            if (resolvedPlatform != null) {
                var found = orderRepository.findByPlatformOrderId(tenantId, resolvedPlatform, externalOrderId.trim());
                if (found.isPresent()) {
                    return found.get();
                }
            }
            // 未记录平台或平台不一致时，退化为按订单号匹配
            var byNumber = orderRepository.findByOrderNumber(externalOrderId.trim());
            return byNumber.orElseThrow(() -> new EntityNotFoundException(
                    "系统内不存在该平台订单，platformOrderId=" + externalOrderId));
        }
        throw new ValidationException("缺少订单标识（orderId / orderNumber / 平台订单号 至少提供一个）");
    }

    /**
     * 按 channelId / channelCode / channelName 定位物流渠道。
     *
     * @throws EntityNotFoundException 渠道不存在
     */
    public LogisticsChannel resolveChannel(String channelId, String channelCode) {
        if (StringUtils.hasText(channelId)) {
            return logisticsChannelService.findById(channelId.trim())
                    .orElseThrow(() -> new EntityNotFoundException("物流渠道不存在，channelId=" + channelId));
        }
        if (StringUtils.hasText(channelCode)) {
            String trimmed = channelCode.trim();
            return logisticsChannelService.findByChannelCode(trimmed)
                    .or(() -> logisticsChannelService.findByChannelCode(trimmed.toUpperCase()))
                    .orElseThrow(() -> new EntityNotFoundException("物流渠道不存在，渠道编码/名称=" + trimmed));
        }
        throw new ValidationException("缺少物流渠道标识（logisticsChannelId / channelCode 至少提供一个）");
    }

    /**
     * 宽松版渠道定位：找不到时返回 null，由调用方决定回退策略。
     */
    public LogisticsChannel findChannelQuietly(String channelCode) {
        if (!StringUtils.hasText(channelCode)) {
            return null;
        }
        return logisticsChannelService.findByChannelCode(channelCode.trim()).orElse(null);
    }

    /**
     * 按「渠道ID 优先、渠道编码/名称 其次」定位渠道，用于调用方无法区分两者口径的场景（如消息与编排链路）。
     *
     * @throws EntityNotFoundException 渠道不存在
     */
    public LogisticsChannel resolveChannelByIdOrCode(String channelIdentifier) {
        if (!StringUtils.hasText(channelIdentifier)) {
            throw new ValidationException("缺少物流渠道标识");
        }
        String trimmed = channelIdentifier.trim();
        return logisticsChannelService.findById(trimmed)
                .or(() -> logisticsChannelService.findByChannelCode(trimmed))
                .orElseThrow(() -> new EntityNotFoundException("物流渠道不存在: " + channelIdentifier));
    }
}
