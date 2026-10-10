package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.logistics;

import org.mapstruct.Mapper;
import site.hexaarch.ecommerce.logistics.domain.logistics.entity.LogisticsChannel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.logistics.LogisticsChannelJpaEntity;

/**
 * 物流渠道映射器，用于在物流渠道领域实体和JPA实体之间进行转换。
 *
 * @author kenyon
 */
@Mapper(componentModel = "spring")
public interface LogisticsChannelMapper {

    /**
     * 将物流渠道领域实体转换为JPA实体
     */
    default LogisticsChannelJpaEntity toJpaEntity(LogisticsChannel channel) {
        if (channel == null) {
            return null;
        }
        LogisticsChannelJpaEntity entity = new LogisticsChannelJpaEntity();
        entity.setId(channel.getChannelId());
        entity.setChannelCode(channel.getChannelCode());
        entity.setChannelName(channel.getChannelName());
        entity.setDescription(channel.getDescription());
        entity.setCountries(channel.getCountries() == null || channel.getCountries().isEmpty()
                ? null : String.join(",", channel.getCountries()));
        entity.setBasePrice(channel.getBasePrice());
        entity.setPricePerGram(channel.getPricePerGram());
        entity.setCurrency(channel.getCurrency());
        entity.setActive(channel.isActive());
        return entity;
    }

    /**
     * 将物流渠道JPA实体转换为领域实体
     */
    default LogisticsChannel toDomainEntity(LogisticsChannelJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return LogisticsChannel.builder()
                .channelId(entity.getId())
                .channelCode(entity.getChannelCode())
                .channelName(entity.getChannelName())
                .description(entity.getDescription())
                .countries(splitCountries(entity.getCountries()))
                .basePrice(entity.getBasePrice())
                .pricePerGram(entity.getPricePerGram())
                .currency(entity.getCurrency())
                .active(entity.isActive())
                .build();
    }

    private static List<String> splitCountries(String countries) {
        if (countries == null || countries.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(countries.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
