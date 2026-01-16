package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository;

import site.hexaarch.ecommerce.logistics.domain.logistics.aggregate.LogisticsOrder;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsOrderRepository;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.LogisticsOrderMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.LogisticsOrderJpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 物流单仓库实现类，使用JPA实现持久化。
 *
 * @author kenyon
 */
public class LogisticsOrderRepositoryImpl implements LogisticsOrderRepository {
    private final LogisticsOrderJpaRepository logisticsOrderJpaRepository;
    private final LogisticsOrderMapper logisticsOrderMapper;

    // 手动添加构造函数，避免Lombok注解问题
    public LogisticsOrderRepositoryImpl(LogisticsOrderJpaRepository logisticsOrderJpaRepository, LogisticsOrderMapper logisticsOrderMapper) {
        this.logisticsOrderJpaRepository = logisticsOrderJpaRepository;
        this.logisticsOrderMapper = logisticsOrderMapper;
    }

    @Override
    public LogisticsOrder save(LogisticsOrder logisticsOrder) {
        var logisticsOrderJpaEntity = logisticsOrderMapper.toJpaEntity(logisticsOrder);
        var savedEntity = logisticsOrderJpaRepository.save(logisticsOrderJpaEntity);
        return logisticsOrderMapper.toDomainAggregate(savedEntity);
    }

    @Override
    public Optional<LogisticsOrder> findById(String id) {
        return logisticsOrderJpaRepository.findById(id)
                .map(logisticsOrderMapper::toDomainAggregate);
    }

    @Override
    public List<LogisticsOrder> findByOrderId(String orderId) {
        // 简化实现，实际应该调用JPA仓库的方法
        return List.of();
    }

    @Override
    public List<LogisticsOrder> findByLogisticsChannelId(String channelId) {
        // 简化实现，实际应该调用JPA仓库的方法
        return List.of();
    }

    @Override
    public List<LogisticsOrder> findByLogisticsStatus(LogisticsStatus status) {
        // 简化实现，实际应该调用JPA仓库的方法
        return List.of();
    }

    @Override
    public List<LogisticsOrder> findAll() {
        return logisticsOrderJpaRepository.findAll()
                .stream()
                .map(logisticsOrderMapper::toDomainAggregate)
                .toList();
    }

    @Override
    public void delete(String id) {
        logisticsOrderJpaRepository.deleteById(id);
    }
}
