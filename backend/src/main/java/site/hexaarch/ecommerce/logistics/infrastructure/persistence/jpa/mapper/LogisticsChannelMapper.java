package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import site.hexaarch.ecommerce.logistics.domain.logistics.entity.LogisticsChannel;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.LogisticsChannelJpaEntity;

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
    @Mapping(source = "channelId", target = "id")
    @Mapping(source = "channelName", target = "channelName")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "active", target = "active")
    LogisticsChannelJpaEntity toJpaEntity(LogisticsChannel logisticsChannel);

    /**
     * 将JPA实体转换为物流渠道领域实体
     */
    @Mapping(source = "id", target = "channelId")
    @Mapping(source = "channelName", target = "channelName")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "active", target = "active")
    LogisticsChannel toDomainEntity(LogisticsChannelJpaEntity logisticsChannelJpaEntity);
}