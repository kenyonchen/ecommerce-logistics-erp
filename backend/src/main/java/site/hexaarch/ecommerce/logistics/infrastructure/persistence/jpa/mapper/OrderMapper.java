package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import site.hexaarch.ecommerce.logistics.domain.order.aggregate.Order;
import site.hexaarch.ecommerce.logistics.domain.order.entity.OrderItem;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.PaymentInfo;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.ShippingAddress;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.OrderItemJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.OrderJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.OrderStatusJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.PaymentInfoJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.ShippingAddressJpaEntity;
import site.hexaarch.ecommerce.logistics.interfaces.dto.order.OrderDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.order.OrderItemDto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单映射器，用于在订单领域模型和JPA实体之间进行转换。
 *
 * @author kenyon
 */
@Mapper(componentModel = "spring")
public interface OrderMapper {
    /**
     * 将订单领域模型转换为JPA实体
     */
    @Mapping(source = "id", target = "id")
    @Mapping(source = "orderNumber", target = "orderNumber")
    @Mapping(source = "orderStatus", target = "status", qualifiedByName = "orderStatusToJpaEntity")
    @Mapping(source = "shippingAddress", target = "shippingAddress")
    @Mapping(source = "paymentInfo", target = "paymentInfo")
    @Mapping(source = "orderItems", target = "orderItems")
    @Mapping(source = "createdAt", target = "createdAt", qualifiedByName = "localDateTimeToLong")
    @Mapping(source = "updatedAt", target = "updatedAt", qualifiedByName = "localDateTimeToLong")
    OrderJpaEntity toJpaEntity(Order order);

    /**
     * 将JPA实体转换为订单领域模型
     */
    default Order toDomainAggregate(OrderJpaEntity orderJpaEntity) {
        try {
            java.lang.reflect.Constructor<Order> constructor = Order.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Order order = constructor.newInstance();

            // 设置各个字段
            java.lang.reflect.Field idField = Order.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(order, orderJpaEntity.getId());

            java.lang.reflect.Field orderNumberField = Order.class.getDeclaredField("orderNumber");
            orderNumberField.setAccessible(true);
            orderNumberField.set(order, orderJpaEntity.getOrderNumber());

            java.lang.reflect.Field orderStatusField = Order.class.getDeclaredField("orderStatus");
            orderStatusField.setAccessible(true);
            orderStatusField.set(order, orderStatusFromJpaEntity(orderJpaEntity.getStatus()));

            java.lang.reflect.Field shippingAddressField = Order.class.getDeclaredField("shippingAddress");
            shippingAddressField.setAccessible(true);
            shippingAddressField.set(order, toDomainValueObject(orderJpaEntity.getShippingAddress()));

            java.lang.reflect.Field paymentInfoField = Order.class.getDeclaredField("paymentInfo");
            paymentInfoField.setAccessible(true);
            paymentInfoField.set(order, toDomainValueObject(orderJpaEntity.getPaymentInfo()));

            java.lang.reflect.Field orderItemsField = Order.class.getDeclaredField("orderItems");
            orderItemsField.setAccessible(true);
            orderItemsField.set(order, toDomainEntityList(orderJpaEntity.getOrderItems()));

            java.lang.reflect.Field createdAtField = Order.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(order, longToLocalDateTime(orderJpaEntity.getCreatedAt()));

            java.lang.reflect.Field updatedAtField = Order.class.getDeclaredField("updatedAt");
            updatedAtField.setAccessible(true);
            updatedAtField.set(order, longToLocalDateTime(orderJpaEntity.getUpdatedAt()));

            // 初始化domainEvents字段
            java.lang.reflect.Field domainEventsField = Order.class.getDeclaredField("domainEvents");
            domainEventsField.setAccessible(true);
            domainEventsField.set(order, new java.util.ArrayList<>());

            return order;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Order from JPA entity", e);
        }
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
    OrderItemJpaEntity toJpaEntity(OrderItem orderItem);

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
     * 将收货地址值对象转换为JPA实体
     */
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
     * 将付款信息值对象转换为JPA实体
     */
    @Mapping(source = "paymentMethod", target = "paymentMethod")
    @Mapping(source = "paymentStatus", target = "paymentStatus")
    @Mapping(source = "transactionId", target = "transactionId")
    PaymentInfoJpaEntity toJpaEntity(PaymentInfo paymentInfo);

    /**
     * 将付款信息JPA实体转换为值对象
     */
    @Mapping(source = "paymentMethod", target = "paymentMethod")
    @Mapping(source = "paymentStatus", target = "paymentStatus")
    @Mapping(source = "transactionId", target = "transactionId")
    PaymentInfo toDomainValueObject(PaymentInfoJpaEntity paymentInfoJpaEntity);

    /**
     * 将订单状态转换为JPA实体枚举
     */
    @Named("orderStatusToJpaEntity")
    default OrderStatusJpaEntity orderStatusToJpaEntity(OrderStatus status) {
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
    OrderDto toDto(Order order);

    @Named("calculateTotalAmount")
    default BigDecimal calculateTotalAmount(Order order) {
        return order.calculateTotal();
    }

    /**
     * 将订单列表转换为DTO列表
     */
    List<OrderDto> toDtoList(List<Order> orders);
}
