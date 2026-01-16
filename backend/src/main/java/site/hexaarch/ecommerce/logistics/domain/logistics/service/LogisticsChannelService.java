package site.hexaarch.ecommerce.logistics.domain.logistics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.logistics.entity.LogisticsChannel;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsChannelRepository;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsFee;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.PriceRule;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 物流渠道服务，负责管理物流渠道信息和计算物流费用。
 *
 * @author kenyon
 */
@Service
@RequiredArgsConstructor
public class LogisticsChannelService {
    private final LogisticsChannelRepository logisticsChannelRepository;

    /**
     * 根据ID查找物流渠道。
     *
     * @param channelId 物流渠道ID
     * @return 物流渠道实体，如果不存在则返回Optional.empty()
     */
    public Optional<LogisticsChannel> findById(String channelId) {
        return logisticsChannelRepository.findById(channelId);
    }

    /**
     * 获取可用的物流渠道。
     *
     * @param country 国家代码
     * @return 物流渠道实体列表
     */
    public List<LogisticsChannel> getAvailableChannels(String country) {
        // 查找支持指定国家的激活物流渠道
        return logisticsChannelRepository.findByCountry(country);
    }

    /**
     * 计算物流费用。
     *
     * @param channelId 物流渠道ID
     * @param weight    重量
     * @param volume    体积
     * @param country   国家代码
     * @return 物流费用
     */
    public LogisticsFee calculateFee(String channelId, double weight, double volume, String country) {
        // 查找物流渠道
        LogisticsChannel logisticsChannel = logisticsChannelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("Logistics channel not found: " + channelId));

        // 验证物流渠道是否支持指定国家
        if (!logisticsChannel.supportsCountry(country)) {
            throw new IllegalArgumentException("Logistics channel does not support country: " + country);
        }

        // 查找适用的价格规则
        PriceRule applicableRule = logisticsChannel.getPriceRules().stream()
                .filter(rule -> rule.isApplicable(weight, volume))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No applicable price rule found for weight: " + weight + " and volume: " + volume));

        // 计算物流费用
        return LogisticsFee.builder()
                .baseFee(applicableRule.getPrice())
                .weightFee(BigDecimal.ZERO)
                .volumeFee(BigDecimal.ZERO)
                .totalFee(applicableRule.getPrice())
                .currency(applicableRule.getCurrency())
                .build();
    }

    /**
     * 创建物流渠道。
     *
     * @param logisticsChannel 物流渠道实体
     * @return 创建的物流渠道实体
     */
    public LogisticsChannel createLogisticsChannel(LogisticsChannel logisticsChannel) {
        // 保存物流渠道
        return logisticsChannelRepository.save(logisticsChannel);
    }

    /**
     * 更新物流渠道。
     *
     * @param logisticsChannel 物流渠道实体
     * @return 更新后的物流渠道实体
     */
    public LogisticsChannel updateLogisticsChannel(LogisticsChannel logisticsChannel) {
        // 验证物流渠道是否存在
        logisticsChannelRepository.findById(logisticsChannel.getChannelId())
                .orElseThrow(() -> new IllegalArgumentException("Logistics channel not found: " + logisticsChannel.getChannelId()));

        // 保存物流渠道
        return logisticsChannelRepository.save(logisticsChannel);
    }
}