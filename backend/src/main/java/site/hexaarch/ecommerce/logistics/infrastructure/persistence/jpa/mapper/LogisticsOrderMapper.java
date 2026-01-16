package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import site.hexaarch.ecommerce.logistics.domain.logistics.aggregate.LogisticsOrder;
import site.hexaarch.ecommerce.logistics.domain.logistics.entity.LogisticsChannel;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsFee;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsLabel;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.LogisticsChannelJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.LogisticsFeeJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.LogisticsLabelJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.LogisticsOrderJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.LogisticsStatusJpaEntity;

/**
 * 物流单映射器，用于在物流单领域模型和JPA实体之间进行转换。
 *
 * @author kenyon
 */
@Mapper(componentModel = "spring")
public interface LogisticsOrderMapper {
    /**
     * 将物流单领域模型转换为JPA实体
     */
    @Mapping(source = "logisticsOrderId", target = "id")
    @Mapping(source = "orderId", target = "orderId")
    @Mapping(source = "logisticsChannelId", target = "logisticsChannel.id")
    @Mapping(source = "logisticsStatus", target = "status", qualifiedByName = "logisticsStatusToJpaEntity")
    @Mapping(source = "logisticsLabel", target = "logisticsLabel")
    @Mapping(source = "logisticsFee", target = "logisticsFee")
    @Mapping(source = "trackingNumber", target = "trackingNumber")
    @Mapping(source = "createdAt", target = "createdAt", qualifiedByName = "localDateTimeToLong")
    @Mapping(source = "updatedAt", target = "updatedAt", qualifiedByName = "localDateTimeToLong")
    LogisticsOrderJpaEntity toJpaEntity(LogisticsOrder logisticsOrder);

    /**
     * 将JPA实体转换为物流单领域模型
     */
    default LogisticsOrder toDomainAggregate(LogisticsOrderJpaEntity logisticsOrderJpaEntity) {
        try {
            java.lang.reflect.Constructor<LogisticsOrder> constructor = LogisticsOrder.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            LogisticsOrder logisticsOrder = constructor.newInstance();

            // 设置各个字段
            java.lang.reflect.Field logisticsOrderIdField = LogisticsOrder.class.getDeclaredField("logisticsOrderId");
            logisticsOrderIdField.setAccessible(true);
            logisticsOrderIdField.set(logisticsOrder, logisticsOrderJpaEntity.getId());

            java.lang.reflect.Field tenantIdField = LogisticsOrder.class.getDeclaredField("tenantId");
            tenantIdField.setAccessible(true);
            tenantIdField.set(logisticsOrder, "default-tenant"); // 暂时设置默认租户ID，实际应用中应该从JPA实体中获取

            java.lang.reflect.Field orderIdField = LogisticsOrder.class.getDeclaredField("orderId");
            orderIdField.setAccessible(true);
            orderIdField.set(logisticsOrder, logisticsOrderJpaEntity.getOrderId());

            java.lang.reflect.Field logisticsChannelIdField = LogisticsOrder.class.getDeclaredField("logisticsChannelId");
            logisticsChannelIdField.setAccessible(true);
            logisticsChannelIdField.set(logisticsOrder, logisticsOrderJpaEntity.getLogisticsChannel().getId());

            java.lang.reflect.Field logisticsStatusField = LogisticsOrder.class.getDeclaredField("logisticsStatus");
            logisticsStatusField.setAccessible(true);
            logisticsStatusField.set(logisticsOrder, logisticsStatusFromJpaEntity(logisticsOrderJpaEntity.getStatus()));

            java.lang.reflect.Field logisticsLabelField = LogisticsOrder.class.getDeclaredField("logisticsLabel");
            logisticsLabelField.setAccessible(true);
            logisticsLabelField.set(logisticsOrder, toDomainValueObject(logisticsOrderJpaEntity.getLogisticsLabel()));

            java.lang.reflect.Field logisticsFeeField = LogisticsOrder.class.getDeclaredField("logisticsFee");
            logisticsFeeField.setAccessible(true);
            logisticsFeeField.set(logisticsOrder, toDomainValueObject(logisticsOrderJpaEntity.getLogisticsFee()));

            java.lang.reflect.Field trackingNumberField = LogisticsOrder.class.getDeclaredField("trackingNumber");
            trackingNumberField.setAccessible(true);
            trackingNumberField.set(logisticsOrder, logisticsOrderJpaEntity.getTrackingNumber());

            java.lang.reflect.Field createdAtField = LogisticsOrder.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(logisticsOrder, longToLocalDateTime(logisticsOrderJpaEntity.getCreatedAt()));

            java.lang.reflect.Field updatedAtField = LogisticsOrder.class.getDeclaredField("updatedAt");
            updatedAtField.setAccessible(true);
            updatedAtField.set(logisticsOrder, longToLocalDateTime(logisticsOrderJpaEntity.getUpdatedAt()));

            // 初始化domainEvents字段
            java.lang.reflect.Field domainEventsField = LogisticsOrder.class.getDeclaredField("domainEvents");
            domainEventsField.setAccessible(true);
            domainEventsField.set(logisticsOrder, new java.util.ArrayList<>());

            return logisticsOrder;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create LogisticsOrder from JPA entity", e);
        }
    }

    /**
     * 将物流渠道领域实体转换为JPA实体
     */
    @Mapping(source = "channelId", target = "id")
    @Mapping(source = "channelName", target = "channelName")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "active", target = "active")
    LogisticsChannelJpaEntity toJpaEntity(LogisticsChannel logisticsChannel);

    /**
     * 将物流渠道JPA实体转换为领域实体
     */
    @Mapping(source = "id", target = "channelId")
    @Mapping(source = "channelName", target = "channelName")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "active", target = "active")
    LogisticsChannel toDomainEntity(LogisticsChannelJpaEntity logisticsChannelJpaEntity);

    /**
     * 将物流标签值对象转换为JPA实体
     */
    @Mapping(source = "labelId", target = "id")
    @Mapping(source = "barcode", target = "labelData")
    LogisticsLabelJpaEntity toJpaEntity(LogisticsLabel logisticsLabel);

    /**
     * 将物流标签JPA实体转换为值对象
     */
    @Mapping(source = "id", target = "labelId")
    @Mapping(source = "labelData", target = "barcode")
    LogisticsLabel toDomainValueObject(LogisticsLabelJpaEntity logisticsLabelJpaEntity);

    /**
     * 将物流费用值对象转换为JPA实体
     */
    @Mapping(source = "feeId", target = "id")
    @Mapping(source = "baseFee", target = "baseFee")
    @Mapping(source = "weightFee", target = "weightFee")
    @Mapping(source = "totalFee", target = "totalFee")
    @Mapping(source = "currency", target = "currency")
    LogisticsFeeJpaEntity toJpaEntity(LogisticsFee logisticsFee);

    /**
     * 将物流费用JPA实体转换为值对象
     */
    @Mapping(source = "id", target = "feeId")
    @Mapping(source = "baseFee", target = "baseFee")
    @Mapping(source = "weightFee", target = "weightFee")
    @Mapping(source = "totalFee", target = "totalFee")
    @Mapping(source = "currency", target = "currency")
    LogisticsFee toDomainValueObject(LogisticsFeeJpaEntity logisticsFeeJpaEntity);

    /**
     * 将物流状态转换为JPA实体枚举
     */
    @Named("logisticsStatusToJpaEntity")
    default LogisticsStatusJpaEntity logisticsStatusToJpaEntity(LogisticsStatus status) {
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
     * 将JPA实体枚举转换为物流状态
     */
    @Named("logisticsStatusFromJpaEntity")
    default LogisticsStatus logisticsStatusFromJpaEntity(LogisticsStatusJpaEntity status) {
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

    /**
     * 将LocalDateTime转换为Long（时间戳）
     */
    @Named("localDateTimeToLong")
    default Long localDateTimeToLong(java.time.LocalDateTime dateTime) {
        return dateTime != null ? dateTime.toEpochSecond(java.time.ZoneOffset.UTC) * 1000 : null;
    }

    /**
     * 将Long（时间戳）转换为LocalDateTime
     */
    @Named("longToLocalDateTime")
    default java.time.LocalDateTime longToLocalDateTime(Long timestamp) {
        return timestamp != null ? java.time.LocalDateTime.ofEpochSecond(timestamp / 1000, 0, java.time.ZoneOffset.UTC) : null;
    }

    /**
     * 将UUID转换为String
     */
    @Named("uuidToString")
    default String uuidToString(java.util.UUID uuid) {
        return uuid != null ? uuid.toString() : null;
    }
}
