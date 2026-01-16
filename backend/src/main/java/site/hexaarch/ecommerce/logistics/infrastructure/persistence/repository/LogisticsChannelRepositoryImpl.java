package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository;

import site.hexaarch.ecommerce.logistics.domain.logistics.entity.LogisticsChannel;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsChannelRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.LogisticsChannelMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.LogisticsChannelJpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 物流渠道仓库实现类，使用JPA实现持久化。
 *
 * @author kenyon
 */
public class LogisticsChannelRepositoryImpl implements LogisticsChannelRepository {
    private final LogisticsChannelJpaRepository logisticsChannelJpaRepository;
    private final LogisticsChannelMapper logisticsChannelMapper;

    // 手动添加构造函数，避免Lombok注解问题
    public LogisticsChannelRepositoryImpl(LogisticsChannelJpaRepository logisticsChannelJpaRepository, LogisticsChannelMapper logisticsChannelMapper) {
        this.logisticsChannelJpaRepository = logisticsChannelJpaRepository;
        this.logisticsChannelMapper = logisticsChannelMapper;
    }

    @Override
    public LogisticsChannel save(LogisticsChannel logisticsChannel) {
        var logisticsChannelJpaEntity = logisticsChannelMapper.toJpaEntity(logisticsChannel);
        var savedEntity = logisticsChannelJpaRepository.save(logisticsChannelJpaEntity);
        return logisticsChannelMapper.toDomainEntity(savedEntity);
    }

    @Override
    public Optional<LogisticsChannel> findById(String id) {
        return logisticsChannelJpaRepository.findById(id)
                .map(logisticsChannelMapper::toDomainEntity);
    }

    @Override
    public List<LogisticsChannel> findByCountry(String country) {
        // 简化实现，实际应该调用JPA仓库的方法
        return List.of();
    }

    @Override
    public List<LogisticsChannel> findActiveChannels() {
        return logisticsChannelJpaRepository.findByActiveTrue()
                .stream()
                .map(logisticsChannelMapper::toDomainEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<LogisticsChannel> findAll() {
        return logisticsChannelJpaRepository.findAll()
                .stream()
                .map(logisticsChannelMapper::toDomainEntity)
                .toList();
    }

    @Override
    public void delete(String id) {
        logisticsChannelJpaRepository.deleteById(id);
    }
}
