package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.order;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import site.hexaarch.ecommerce.logistics.domain.order.aggregate.Order;
import site.hexaarch.ecommerce.logistics.domain.order.entity.OrderItem;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.PaymentInfo;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.ShippingAddress;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.order.OrderItemJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.order.OrderJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.order.OrderStatusJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.order.PaymentInfoJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.order.ShippingAddressJpaEntity;
import site.hexaarch.ecommerce.logistics.interfaces.dto.order.OrderDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.order.OrderItemDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单映射器，用于在订单领域模型和JPA实体之间进行转换。
 *
 * @author kenyon
 */
@Mapper(componentModel = "spring")
public interface OrderMapper {

    /**
     * 将LocalDateTime转换为毫秒时间戳（接口静态方法，供 Specification 等复用）
     */
    static Long toEpochMillis(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toEpochSecond(java.time.ZoneOffset.UTC) * 1000;
    }

    /**
     * 将订单领域模型转换为JPA实体
     */
    @Mapping(source = "id", target = "id")
    @Mapping(source = "tenantId", target = "tenantId")
    @Mapping(source = "customerId", target = "customerId")
    @Mapping(source = "customerName", target = "customerName")
    @Mapping(source = "customerEmail", target = "customerEmail")
    @Mapping(source = "orderNumber", target = "orderNumber")
    @Mapping(source = "platform", target = "platform")
    @Mapping(source = "platformOrderId", target = "platformOrderId")
    @Mapping(source = "orderStatus", target = "status", qualifiedByName = "orderStatusToJpaEntity")
    @Mapping(source = "shippingAddress", target = "shippingAddress")
    @Mapping(source = "paymentInfo", target = "paymentInfo")
    @Mapping(source = "orderItems", target = "orderItems")
    @Mapping(source = "createdAt", target = "createdAt", qualifiedByName = "localDateTimeToLong")
    @Mapping(source = "updatedAt", target = "updatedAt", qualifiedByName = "localDateTimeToLong")
    @Mapping(target = "totalAmount", ignore = true)
    OrderJpaEntity toJpaEntity(Order order);

    /**
     * 将JPA实体转换为订单领域模型。
     * <p>
     * 领域聚合字段为私有且仅提供 protected setter，因此这里统一通过反射装配，
     * 并保证 customerId / tenantId / platform / platformOrderId 等字段不丢失。
     * </p>
     */
    default Order toDomainAggregate(OrderJpaEntity orderJpaEntity) {
        if (orderJpaEntity == null) {
            return null;
        }
        try {
            java.lang.reflect.Constructor<Order> constructor = Order.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Order order = constructor.newInstance();

            setField(order, "id", orderJpaEntity.getId());
            setField(order, "tenantId", orderJpaEntity.getTenantId());
            setField(order, "customerId", orderJpaEntity.getCustomerId());
            setField(order, "customerName", orderJpaEntity.getCustomerName());
            setField(order, "customerEmail", orderJpaEntity.getCustomerEmail());
            setField(order, "orderNumber", orderJpaEntity.getOrderNumber());
            setField(order, "platform", orderJpaEntity.getPlatform());
            setField(order, "platformOrderId", orderJpaEntity.getPlatformOrderId());
            setField(order, "orderStatus", orderStatusFromJpaEntity(orderJpaEntity.getStatus()));
            setField(order, "shippingAddress", toDomainValueObject(orderJpaEntity.getShippingAddress()));
            setField(order, "paymentInfo", toDomainValueObject(orderJpaEntity.getPaymentInfo()));
            setField(order, "orderItems", toDomainEntityList(orderJpaEntity.getOrderItems()));
            setField(order, "createdAt", longToLocalDateTime(orderJpaEntity.getCreatedAt()));
            setField(order, "updatedAt", longToLocalDateTime(orderJpaEntity.getUpdatedAt()));
            // 初始化domainEvents字段
            setField(order, "domainEvents", new java.util.ArrayList<>());

            return order;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Order from JPA entity", e);
        }
    }

    private static void setField(Order order, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = Order.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(order, value);
    }

    /**
     * 将订单行项领域实体转换为JPA实体
     */
    @Mapping(source = "id", target = "id")
    @Mapping(source = "productName", target = "productName")
    @Mapping(source = "sku", target = "skuCode")
    @Mapping(source = "quantity", target = "quantity")
    @Mapping(source = "unitPrice", target = "unitPrice")
    @Mapping(source = "totalPrice", target = "totalPrice")
    @Mapping(target = "order", ignore = true)
    OrderItemJpaEntity toJpaEntity(OrderItem orderItem);

    /**
     * 订单行项主键为手工赋值（无 @GeneratedValue），新建行项由 DTO 转换而来时 id 为空，
     * persist 前必须补 UUID，否则 Hibernate 抛 IdentifierGenerationException。
     */
    @AfterMapping
    default void assignOrderIdWhenAbsent(@MappingTarget OrderItemJpaEntity entity) {
        if (entity.getId() == null || entity.getId().isBlank()) {
            entity.setId(java.util.UUID.randomUUID().toString());
        }
    }

    /**
     * 将订单行项JPA实体转换为领域实体
     */
    @Mapping(source = "id", target = "id")
    @Mapping(source = "productName", target = "productName")
    @Mapping(source = "skuCode", target = "sku")
    @Mapping(source = "quantity", target = "quantity")
    @Mapping(source = "unitPrice", target = "unitPrice")
    @Mapping(source = "totalPrice", target = "totalPrice")
    OrderItem toDomainEntity(OrderItemJpaEntity orderItemJpaEntity);

    /**
     * 将收货地址值对象转换为JPA实体（JPA实体主键为手工赋值，这里统一生成UUID）
     */
    @Mapping(target = "id", expression = "java(java.util.UUID.randomUUID().toString())")
    @Mapping(source = "recipient", target = "recipientName")
    @Mapping(source = "street", target = "addressLine1")
    @Mapping(source = "city", target = "city")
    @Mapping(source = "province", target = "stateProvince")
    @Mapping(source = "zipCode", target = "postalCode")
    @Mapping(source = "country", target = "country")
    @Mapping(source = "phone", target = "phoneNumber")
    ShippingAddressJpaEntity toJpaEntity(ShippingAddress shippingAddress);

    /**
     * 将收货地址JPA实体转换为值对象
     */
    @Mapping(source = "recipientName", target = "recipient")
    @Mapping(source = "addressLine1", target = "street")
    @Mapping(source = "city", target = "city")
    @Mapping(source = "stateProvince", target = "province")
    @Mapping(source = "postalCode", target = "zipCode")
    @Mapping(source = "country", target = "country")
    @Mapping(source = "phoneNumber", target = "phone")
    ShippingAddress toDomainValueObject(ShippingAddressJpaEntity shippingAddressJpaEntity);

    /**
     * 将付款信息值对象转换为JPA实体（JPA实体主键为手工赋值，这里统一生成UUID）
     */
    @Mapping(target = "id", expression = "java(java.util.UUID.randomUUID().toString())")
    @Mapping(source = "paymentMethod", target = "paymentMethod")
    @Mapping(source = "paymentStatus", target = "paymentStatus")
    @Mapping(source = "transactionId", target = "transactionId")
    @Mapping(source = "amount", target = "amount")
    PaymentInfoJpaEntity toJpaEntity(PaymentInfo paymentInfo);

    /**
     * 将付款信息JPA实体转换为值对象。
     * <p>
     * amount 缺失时回退为0，避免 {@link PaymentInfo} 构造器的非空校验导致读订单直接NPE。
     * </p>
     */
    default PaymentInfo toDomainValueObject(PaymentInfoJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        BigDecimal amount = entity.getAmount() == null ? BigDecimal.ZERO : entity.getAmount();
        return PaymentInfo.builder()
                .paymentMethod(defaultString(entity.getPaymentMethod()))
                .paymentStatus(defaultString(entity.getPaymentStatus()))
                .transactionId(defaultString(entity.getTransactionId()))
                .amount(amount)
                .build();
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }

    /**
     * 将订单状态转换为JPA实体枚举
     */
    @Named("orderStatusToJpaEntity")
    default OrderStatusJpaEntity orderStatusToJpaEntity(OrderStatus status) {
        if (status == null) {
            return OrderStatusJpaEntity.PENDING;
        }
        return switch (status) {
            case PENDING -> OrderStatusJpaEntity.PENDING;
            case PROCESSING -> OrderStatusJpaEntity.PROCESSING;
            case CON_FIRMED -> OrderStatusJpaEntity.CONFIRMED;
            case SHIPPED -> OrderStatusJpaEntity.SHIPPED;
            case COMPLETED -> OrderStatusJpaEntity.COMPLETED;
            case CANCELLED -> OrderStatusJpaEntity.CANCELLED;
        };
    }

    /**
     * 将JPA实体枚举转换为订单状态
     */
    @Named("orderStatusFromJpaEntity")
    default OrderStatus orderStatusFromJpaEntity(OrderStatusJpaEntity status) {
        if (status == null) {
            return OrderStatus.PENDING;
        }
        return switch (status) {
            case PENDING -> OrderStatus.PENDING;
            case PROCESSING -> OrderStatus.PROCESSING;
            case CONFIRMED -> OrderStatus.CON_FIRMED;
            case SHIPPED -> OrderStatus.SHIPPED;
            case COMPLETED -> OrderStatus.COMPLETED;
            case CANCELLED -> OrderStatus.CANCELLED;
        };
    }

    /**
     * 将订单行项列表转换为JPA实体列表
     */
    List<OrderItemJpaEntity> toJpaEntityList(List<OrderItem> orderItems);

    /**
     * 将订单行项JPA实体列表转换为领域实体列表
     */
    List<OrderItem> toDomainEntityList(List<OrderItemJpaEntity> orderItemJpaEntities);

    /**
     * 将LocalDateTime转换为Long（时间戳）
     */
    @Named("localDateTimeToLong")
    default Long localDateTimeToLong(LocalDateTime dateTime) {
        return toEpochMillis(dateTime);
    }

    /**
     * 将Long（时间戳）转换为LocalDateTime
     */
    @Named("longToLocalDateTime")
    default LocalDateTime longToLocalDateTime(Long timestamp) {
        return timestamp == null ? null
                : LocalDateTime.ofEpochSecond(timestamp / 1000, 0, java.time.ZoneOffset.UTC);
    }

    /**
     * 将OrderItemDto转换为OrderItem
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(source = "skuCode", target = "productId")
    @Mapping(source = "skuCode", target = "sku")
    @Mapping(source = "productName", target = "productName")
    @Mapping(source = "quantity", target = "quantity")
    @Mapping(source = "unitPrice", target = "unitPrice")
    @Mapping(source = "totalPrice", target = "totalPrice")
    OrderItem toDomainEntity(OrderItemDto orderItemDto);

    /**
     * 将OrderItemDto列表转换为OrderItem列表
     */
    List<OrderItem> toDomainEntityItemList(List<OrderItemDto> orderItemDtos);

    /**
     * 将订单领域模型转换为DTO
     */
    @Mapping(source = "id", target = "orderId")
    @Mapping(source = "orderStatus", target = "status")
    @Mapping(source = ".", target = "totalAmount", qualifiedByName = "calculateTotalAmount")
    @Mapping(source = "customerName", target = "customerName")
    @Mapping(source = "customerEmail", target = "customerEmail")
    @Mapping(source = "platform", target = "platform")
    @Mapping(source = "platformOrderId", target = "platformOrderId")
    @Mapping(target = "statusLabel", ignore = true)
    @Mapping(target = "allowedNextStatuses", ignore = true)
    OrderDto toDto(Order order);

    /**
     * 订单行项 -> 行项DTO（sku -> skuCode，BigDecimal -> Double）
     */
    default OrderItemDto toDtoItem(OrderItem item) {
        if (item == null) {
            return null;
        }
        OrderItemDto dto = new OrderItemDto();
        dto.setProductName(item.getProductName());
        dto.setSkuCode(item.getSku());
        dto.setQuantity(item.getQuantity());
        dto.setUnitPrice(item.getUnitPrice() == null ? null : item.getUnitPrice().doubleValue());
        dto.setTotalPrice(item.getTotalPrice() == null ? null : item.getTotalPrice().doubleValue());
        return dto;
    }

    /**
     * 订单行项列表 -> 行项DTO列表
     */
    List<OrderItemDto> toDtoItemList(List<OrderItem> items);

    @Named("calculateTotalAmount")
    default BigDecimal calculateTotalAmount(Order order) {
        return order == null ? BigDecimal.ZERO : order.calculateTotal();
    }

    /**
     * 将订单列表转换为DTO列表
     */
    List<OrderDto> toDtoList(List<Order> orders);
}
