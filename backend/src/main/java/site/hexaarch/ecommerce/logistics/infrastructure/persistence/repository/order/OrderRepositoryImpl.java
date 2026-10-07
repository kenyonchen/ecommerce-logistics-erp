package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.order;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.order.aggregate.Order;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderQuery;
import site.hexaarch.ecommerce.logistics.domain.order.repository.OrderRepository;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.order.OrderItemJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.order.OrderJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.order.OrderMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.order.OrderJpaRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.specification.order.OrderSpecification;

/**
 * 订单仓库实现类，使用JPA实现持久化。
 *
 * @author kenyon
 */
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {
    private final OrderJpaRepository orderJpaRepository;
    private final OrderMapper orderMapper;

    @Override
    public Order save(Order order) {
        OrderJpaEntity orderJpaEntity = orderMapper.toJpaEntity(order);
        // 订单行项的关联由 OrderItemJpaEntity 持有，必须回填，否则 order_id 外键为空
        if (orderJpaEntity.getOrderItems() != null) {
            for (OrderItemJpaEntity item : orderJpaEntity.getOrderItems()) {
                item.setOrder(orderJpaEntity);
            }
        }
        OrderJpaEntity savedEntity = orderJpaRepository.save(orderJpaEntity);
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
        return orderJpaRepository.findByStatus(orderMapper.orderStatusToJpaEntity(status))
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

    @Override
    public Optional<Order> findByOrderNumber(String orderNumber) {
        return orderJpaRepository.findByOrderNumber(orderNumber)
                .map(orderMapper::toDomainAggregate);
    }

    @Override
    public Optional<Order> findByPlatformOrderId(String tenantId, String platform, String platformOrderId) {
        if (tenantId == null || platform == null || platformOrderId == null) {
            return Optional.empty();
        }
        return orderJpaRepository.findByTenantIdAndPlatformAndPlatformOrderId(tenantId, platform, platformOrderId)
                .map(orderMapper::toDomainAggregate);
    }

    @Override
    public boolean existsByPlatformOrderId(String tenantId, String platform, String platformOrderId) {
        if (tenantId == null || platform == null || platformOrderId == null) {
            return false;
        }
        return orderJpaRepository.existsByTenantIdAndPlatformAndPlatformOrderId(tenantId, platform, platformOrderId);
    }

    @Override
    public PageResult<Order> search(OrderQuery query, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        var pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = orderJpaRepository.findAll(OrderSpecification.withQuery(query), pageable);
        List<Order> orders = new ArrayList<>(result.getNumberOfElements());
        for (OrderJpaEntity entity : result.getContent()) {
            orders.add(orderMapper.toDomainAggregate(entity));
        }
        return new PageResult<>(orders, result.getTotalElements(), result.getNumber(), result.getSize());
    }
}
