package site.hexaarch.ecommerce.logistics.domain.logistics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.logistics.entity.LogisticsChannel;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsChannelRepository;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsOrderRepository;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsFee;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final LogisticsOrderRepository logisticsOrderRepository;

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
     * 根据渠道编码或渠道名称查找物流渠道（平台物流数据导入时按编码匹配）。
     */
    public Optional<LogisticsChannel> findByChannelCode(String channelCode) {
        if (channelCode == null || channelCode.isBlank()) {
            return Optional.empty();
        }
        String normalized = channelCode.trim();
        return logisticsChannelRepository.findAll().stream()
                .filter(channel -> normalized.equalsIgnoreCase(channel.getChannelCode())
                        || normalized.equals(channel.getChannelName()))
                .findFirst();
    }

    /**
     * 查找全部物流渠道（含停用渠道）。
     */
    public List<LogisticsChannel> findAll() {
        return logisticsChannelRepository.findAll();
    }

    /**
     * 获取可用的物流渠道。
     *
     * @param country 国家代码，为空时返回全部启用渠道
     * @return 物流渠道实体列表
     */
    public List<LogisticsChannel> getAvailableChannels(String country) {
        if (country == null || country.isBlank()) {
            return logisticsChannelRepository.findActiveChannels();
        }
        return logisticsChannelRepository.findByCountry(country);
    }

    /**
     * 计算物流费用：首重费用 + 重量（克）× 每克续重费用。
     *
     * @param channelId 物流渠道ID
     * @param weight    重量（克）
     * @param country   国家代码
     * @return 物流费用
     */
    public LogisticsFee calculateFee(String channelId, double weight, String country) {
        LogisticsChannel logisticsChannel = logisticsChannelRepository.findById(channelId)
                .orElseThrow(() -> new EntityNotFoundException("物流渠道不存在: " + channelId));

        if (!logisticsChannel.supportsCountry(country)) {
            throw new ValidationException("物流渠道不支持该国家/地区: " + country);
        }

        BigDecimal weightFee = logisticsChannel.getPricePerGram()
                .multiply(BigDecimal.valueOf(Math.max(weight, 0D)))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal baseFee = logisticsChannel.getBasePrice().setScale(2, RoundingMode.HALF_UP);
        return LogisticsFee.builder()
                .baseFee(baseFee)
                .weightFee(weightFee)
                .totalFee(baseFee.add(weightFee))
                .currency(logisticsChannel.getCurrency())
                .build();
    }

    /**
     * 创建物流渠道，渠道编码重复时拒绝。
     *
     * @param logisticsChannel 物流渠道实体
     * @return 创建的物流渠道实体
     */
    public LogisticsChannel createLogisticsChannel(LogisticsChannel logisticsChannel) {
        if (logisticsChannel.getChannelCode() == null || logisticsChannel.getChannelCode().isBlank()) {
            throw new ValidationException("渠道编码不能为空");
        }
        findByChannelCode(logisticsChannel.getChannelCode()).ifPresent(existing -> {
            throw new ValidationException("渠道编码已存在: " + logisticsChannel.getChannelCode());
        });
        return logisticsChannelRepository.save(logisticsChannel);
    }

    /**
     * 更新物流渠道（按渠道ID整体覆盖，编码冲突时拒绝）。
     *
     * @param logisticsChannel 物流渠道实体
     * @return 更新后的物流渠道实体
     */
    public LogisticsChannel updateLogisticsChannel(LogisticsChannel logisticsChannel) {
        LogisticsChannel existing = logisticsChannelRepository.findById(logisticsChannel.getChannelId())
                .orElseThrow(() -> new EntityNotFoundException("物流渠道不存在: " + logisticsChannel.getChannelId()));
        findByChannelCode(logisticsChannel.getChannelCode())
                .filter(other -> !other.getChannelId().equals(existing.getChannelId()))
                .ifPresent(other -> {
                    throw new ValidationException("渠道编码已存在: " + logisticsChannel.getChannelCode());
                });
        return logisticsChannelRepository.save(logisticsChannel);
    }

    /**
     * 启用/停用物流渠道。
     */
    public LogisticsChannel changeChannelStatus(String channelId, boolean active) {
        LogisticsChannel existing = logisticsChannelRepository.findById(channelId)
                .orElseThrow(() -> new EntityNotFoundException("物流渠道不存在: " + channelId));
        LogisticsChannel updated = active ? existing.activate() : existing.deactivate();
        return logisticsChannelRepository.save(updated);
    }

    /**
     * 删除物流渠道（存在关联物流单时禁止删除，避免物流单丢失渠道）。
     */
    public void deleteLogisticsChannel(String channelId) {
        logisticsChannelRepository.findById(channelId)
                .orElseThrow(() -> new EntityNotFoundException("物流渠道不存在: " + channelId));
        boolean inUse = logisticsOrderRepository.findByLogisticsChannelId(channelId).stream().findAny().isPresent();
        if (inUse) {
            throw new ValidationException("该渠道下存在物流单，无法删除，请改为停用");
        }
        logisticsChannelRepository.delete(channelId);
    }
}