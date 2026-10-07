package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.logistics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Mapper;
import site.hexaarch.ecommerce.logistics.domain.logistics.aggregate.LogisticsOrder;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsFee;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsLabel;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsStatus;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsTrace;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.logistics.LogisticsChannelJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.logistics.LogisticsFeeJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.logistics.LogisticsLabelJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.logistics.LogisticsOrderJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.logistics.LogisticsStatusJpaEntity;

/**
 * 物流单映射器，用于在物流单领域模型和JPA实体之间进行转换。
 * <p>
 * 字段读写全部显式完成，避免依赖反射；轨迹列表以JSON文本落库。
 * </p>
 *
 * @author kenyon
 */
@Mapper(componentModel = "spring")
public interface LogisticsOrderMapper {

    ObjectMapper LOGISTICS_ORDER_JSON = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * 将物流单领域模型转换为JPA实体
     */
    default LogisticsOrderJpaEntity toJpaEntity(LogisticsOrder logisticsOrder) {
        if (logisticsOrder == null) {
            return null;
        }
        LogisticsOrderJpaEntity entity = new LogisticsOrderJpaEntity();
        entity.setId(logisticsOrder.getLogisticsOrderId());
        entity.setLogisticsOrderNumber(logisticsOrder.getLogisticsOrderNumber());
        entity.setTenantId(logisticsOrder.getTenantId());
        entity.setOrderId(logisticsOrder.getOrderId());
        LogisticsChannelJpaEntity channel = new LogisticsChannelJpaEntity();
        channel.setId(logisticsOrder.getLogisticsChannelId());
        entity.setLogisticsChannel(channel);
        entity.setCarrier(logisticsOrder.getCarrier());
        entity.setStatus(toStatusEntity(logisticsOrder.getLogisticsStatus()));
        entity.setLogisticsLabel(toJpaEntity(logisticsOrder.getLogisticsLabel()));
        entity.setLogisticsFee(toJpaEntity(logisticsOrder.getLogisticsFee()));
        entity.setTrackingNumber(logisticsOrder.getTrackingNumber());
        entity.setTrackingUrl(logisticsOrder.getTrackingUrl());
        entity.setPlatform(logisticsOrder.getPlatform());
        entity.setExternalLogisticsId(logisticsOrder.getExternalLogisticsId());
        entity.setShippedAt(logisticsOrder.getShippedAt());
        entity.setEstimatedArrivalAt(logisticsOrder.getEstimatedArrivalAt());
        entity.setDeliveredAt(logisticsOrder.getDeliveredAt());
        entity.setTrackingTraces(toTracesJson(logisticsOrder.getTraces()));
        entity.setCreatedAt(toEpochMillis(logisticsOrder.getCreatedAt()));
        entity.setUpdatedAt(toEpochMillis(logisticsOrder.getUpdatedAt()));
        return entity;
    }

    /**
     * 将JPA实体转换为物流单领域模型
     */
    default LogisticsOrder toDomainAggregate(LogisticsOrderJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return LogisticsOrder.restore(
                entity.getId(),
                entity.getLogisticsOrderNumber(),
                entity.getTenantId(),
                entity.getOrderId(),
                entity.getLogisticsChannel() == null ? null : entity.getLogisticsChannel().getId(),
                entity.getCarrier(),
                entity.getTrackingNumber(),
                entity.getTrackingUrl(),
                entity.getPlatform(),
                entity.getExternalLogisticsId(),
                fromStatusEntity(entity.getStatus()),
                toDomainLabel(entity.getLogisticsLabel()),
                toDomainFee(entity.getLogisticsFee()),
                entity.getShippedAt(),
                entity.getEstimatedArrivalAt(),
                entity.getDeliveredAt(),
                fromTracesJson(entity.getTrackingTraces()),
                fromEpochMillis(entity.getCreatedAt()),
                fromEpochMillis(entity.getUpdatedAt()));
    }

    /**
     * 物流标签值对象 -> JPA实体
     */
    default LogisticsLabelJpaEntity toJpaEntity(LogisticsLabel label) {
        if (label == null) {
            return null;
        }
        LogisticsLabelJpaEntity entity = new LogisticsLabelJpaEntity();
        entity.setId(label.getLabelId() == null ? UUID.randomUUID().toString() : label.getLabelId());
        entity.setTrackingNumber(label.getTrackingNumber());
        if (label.getShipperInfo() != null) {
            entity.setShipperName(label.getShipperInfo().getName());
            entity.setShipperAddress(label.getShipperInfo().getAddress());
            entity.setShipperPhone(label.getShipperInfo().getPhone());
        }
        if (label.getRecipientInfo() != null) {
            entity.setRecipientName(label.getRecipientInfo().getName());
            entity.setRecipientAddress(label.getRecipientInfo().getAddress());
            entity.setRecipientPhone(label.getRecipientInfo().getPhone());
        }
        if (label.getPackageInfo() != null) {
            entity.setPackageWeight(label.getPackageInfo().getWeight());
            entity.setPackageQuantity(label.getPackageInfo().getQuantity());
        }
        entity.setBarcode(label.getBarcode());
        entity.setLabelUrl(label.getLabelUrl());
        entity.setLabelFormat(label.getLabelFormat());
        return entity;
    }

    /**
     * JPA实体 -> 物流标签值对象
     */
    default LogisticsLabel toDomainLabel(LogisticsLabelJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return LogisticsLabel.builder()
                .labelId(entity.getId())
                .trackingNumber(entity.getTrackingNumber())
                .shipperInfo(LogisticsLabel.ShipperInfo.builder()
                        .name(nvl(entity.getShipperName()))
                        .address(nvl(entity.getShipperAddress()))
                        .phone(nvl(entity.getShipperPhone()))
                        .build())
                .recipientInfo(LogisticsLabel.RecipientInfo.builder()
                        .name(nvl(entity.getRecipientName()))
                        .address(nvl(entity.getRecipientAddress()))
                        .phone(nvl(entity.getRecipientPhone()))
                        .build())
                .packageInfo(LogisticsLabel.PackageInfo.builder()
                        .weight(entity.getPackageWeight() == null ? 0D : entity.getPackageWeight())
                        .quantity(entity.getPackageQuantity() == null ? 1 : entity.getPackageQuantity())
                        .build())
                .barcode(entity.getBarcode())
                .labelUrl(entity.getLabelUrl())
                .labelFormat(entity.getLabelFormat())
                .build();
    }

    /**
     * 物流费用值对象 -> JPA实体
     */
    default LogisticsFeeJpaEntity toJpaEntity(LogisticsFee fee) {
        if (fee == null) {
            return null;
        }
        LogisticsFeeJpaEntity entity = new LogisticsFeeJpaEntity();
        entity.setId(fee.getFeeId() == null ? UUID.randomUUID().toString() : fee.getFeeId());
        entity.setBaseFee(toDouble(fee.getBaseFee()));
        entity.setWeightFee(toDouble(fee.getWeightFee()));
        entity.setTotalFee(fee.getTotalFee() == null ? 0D : fee.getTotalFee().doubleValue());
        entity.setCurrency(fee.getCurrency() == null ? "CNY" : fee.getCurrency());
        return entity;
    }

    /**
     * JPA实体 -> 物流费用值对象
     */
    default LogisticsFee toDomainFee(LogisticsFeeJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return LogisticsFee.builder()
                .feeId(entity.getId())
                .baseFee(fromDouble(entity.getBaseFee()))
                .weightFee(fromDouble(entity.getWeightFee()))
                .totalFee(fromDouble(entity.getTotalFee()))
                .currency(entity.getCurrency())
                .build();
    }

    /**
     * 领域物流状态 -> 持久化状态枚举
     */
    default LogisticsStatusJpaEntity toStatusEntity(LogisticsStatus status) {
        if (status == null) {
            return LogisticsStatusJpaEntity.PENDING;
        }
        return switch (status) {
            case PENDING -> LogisticsStatusJpaEntity.PENDING;
            case LABEL_GENERATED -> LogisticsStatusJpaEntity.LABEL_GENERATED;
            case COLLECTED -> LogisticsStatusJpaEntity.PICKED_UP;
            case IN_TRANSIT -> LogisticsStatusJpaEntity.IN_TRANSIT;
            case DELIVERING -> LogisticsStatusJpaEntity.OUT_FOR_DELIVERY;
            case DELIVERED -> LogisticsStatusJpaEntity.DELIVERED;
            case FAILED -> LogisticsStatusJpaEntity.EXCEPTION;
            case CANCELLED -> LogisticsStatusJpaEntity.CANCELLED;
        };
    }

    /**
     * 持久化状态枚举 -> 领域物流状态
     */
    default LogisticsStatus fromStatusEntity(LogisticsStatusJpaEntity status) {
        if (status == null) {
            return LogisticsStatus.PENDING;
        }
        return switch (status) {
            case PENDING -> LogisticsStatus.PENDING;
            case LABEL_GENERATED -> LogisticsStatus.LABEL_GENERATED;
            case PICKED_UP -> LogisticsStatus.COLLECTED;
            case IN_TRANSIT -> LogisticsStatus.IN_TRANSIT;
            case OUT_FOR_DELIVERY -> LogisticsStatus.DELIVERING;
            case DELIVERED -> LogisticsStatus.DELIVERED;
            case EXCEPTION -> LogisticsStatus.FAILED;
            case CANCELLED -> LogisticsStatus.CANCELLED;
        };
    }

    default String toTracesJson(List<LogisticsTrace> traces) {
        if (traces == null || traces.isEmpty()) {
            return null;
        }
        try {
            return LOGISTICS_ORDER_JSON.writeValueAsString(traces);
        } catch (Exception e) {
            return null;
        }
    }

    default List<LogisticsTrace> fromTracesJson(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return LOGISTICS_ORDER_JSON.readValue(json, new TypeReference<List<LogisticsTrace>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    default Long toEpochMillis(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    default LocalDateTime fromEpochMillis(Long millis) {
        return millis == null ? null
                : LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), java.time.ZoneId.systemDefault());
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private static BigDecimal fromDouble(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }
}
