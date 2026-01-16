package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository;

import site.hexaarch.ecommerce.logistics.domain.order.aggregate.Order;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderRepository;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.OrderStatusJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.OrderMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.OrderJpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 订单仓库实现类，使用JPA实现持久化。
 *
 * @author kenyon
 */
public class OrderRepositoryImpl implements OrderRepository {
    private final OrderJpaRepository orderJpaRepository;
    private final OrderMapper orderMapper;

    // 手动添加构造函数，避免Lombok注解问题
    public OrderRepositoryImpl(OrderJpaRepository orderJpaRepository, OrderMapper orderMapper) {
        this.orderJpaRepository = orderJpaRepository;
        this.orderMapper = orderMapper;
    }

    @Override
    public Order save(Order order) {
        var orderJpaEntity = orderMapper.toJpaEntity(order);
        var savedEntity = orderJpaRepository.save(orderJpaEntity);
        return orderMapper.toDomainAggregate(savedEntity);
    }

    @Override
    public Optional<Order> findById(String id) {
        return orderJpaRepository.findById(id)
                .map(orderMapper::toDomainAggregate);
    }

    @Override
    public List<Order> findByCustomerId(String customerId) {
        return orderJpaRepository.findByCustomerId(customerId)
                .stream()
                .map(orderMapper::toDomainAggregate)
                .toList();
    }

    @Override
    public List<Order> findByStatus(OrderStatus status) {
        return orderJpaRepository.findByStatus(OrderStatusJpaEntity.valueOf(status.name()))
                .stream()
                .map(orderMapper::toDomainAggregate)
                .toList();
    }

    @Override
    public List<Order> findAll() {
        return orderJpaRepository.findAll()
                .stream()
                .map(orderMapper::toDomainAggregate)
                .toList();
    }

    @Override
    public void delete(String id) {
        orderJpaRepository.deleteById(id);
    }
}
