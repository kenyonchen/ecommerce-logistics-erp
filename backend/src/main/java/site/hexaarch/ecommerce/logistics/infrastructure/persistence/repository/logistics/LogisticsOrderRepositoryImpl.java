package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.logistics;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.logistics.aggregate.LogisticsOrder;
import site.hexaarch.ecommerce.logistics.domain.logistics.repository.LogisticsOrderRepository;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.logistics.LogisticsOrderJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.logistics.LogisticsOrderMapper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.logistics.LogisticsOrderJpaRepository;

/**
 * 物流单仓库实现类，使用JPA实现持久化。
 *
 * @author kenyon
 */
public class LogisticsOrderRepositoryImpl implements LogisticsOrderRepository {

    private static final int MAX_PAGE_SIZE = 200;

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
    public Optional<LogisticsOrder> findByLogisticsOrderNumber(String logisticsOrderNumber) {
        if (logisticsOrderNumber == null || logisticsOrderNumber.isBlank()) {
            return Optional.empty();
        }
        return logisticsOrderJpaRepository.findByLogisticsOrderNumber(logisticsOrderNumber.trim())
                .map(logisticsOrderMapper::toDomainAggregate);
    }

    @Override
    public List<LogisticsOrder> findByOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return List.of();
        }
        return logisticsOrderJpaRepository.findByOrderId(orderId.trim()).stream()
                .map(logisticsOrderMapper::toDomainAggregate)
                .toList();
    }

    @Override
    public List<LogisticsOrder> findByTrackingNumber(String trackingNumber) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            return List.of();
        }
        return logisticsOrderJpaRepository.findByTrackingNumber(trackingNumber.trim()).stream()
                .map(logisticsOrderMapper::toDomainAggregate)
                .toList();
    }

    @Override
    public Optional<LogisticsOrder> findByPlatformExternalId(String tenantId, String platform,
                                                             String externalLogisticsId) {
        if (tenantId == null || platform == null || externalLogisticsId == null || externalLogisticsId.isBlank()) {
            return Optional.empty();
        }
        return logisticsOrderJpaRepository
                .findFirstByTenantIdAndPlatformAndExternalLogisticsId(tenantId, platform, externalLogisticsId.trim())
                .map(logisticsOrderMapper::toDomainAggregate);
    }

    @Override
    public List<LogisticsOrder> findByLogisticsChannelId(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return List.of();
        }
        return logisticsOrderJpaRepository.findByLogisticsChannelId(channelId.trim()).stream()
                .map(logisticsOrderMapper::toDomainAggregate)
                .toList();
    }

    @Override
    public List<LogisticsOrder> findByLogisticsStatus(LogisticsStatus status) {
        if (status == null) {
            return List.of();
        }
        Specification<LogisticsOrderJpaEntity>
            spec = (root, query, cb) -> cb.equal(root.get("status"), logisticsOrderMapper
                .toStatusEntity(status));
        return logisticsOrderJpaRepository.findAll(spec).stream()
                .map(logisticsOrderMapper::toDomainAggregate)
                .toList();
    }

    @Override
    public PageResult<LogisticsOrder> search(String tenantId, String trackingNumber, String orderId, String channelId,
                                             LogisticsStatus status, String keyword, String startDate, String endDate,
                                             int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<LogisticsOrderJpaEntity> result =
                logisticsOrderJpaRepository.findAll(buildSpec(tenantId, trackingNumber, orderId, channelId, status,
                        keyword, startDate, endDate), pageable);
        List<LogisticsOrder> content = new ArrayList<>(result.getNumberOfElements());
        result.getContent().forEach(entity -> content.add(logisticsOrderMapper.toDomainAggregate(entity)));
        return new PageResult<>(content, result.getTotalElements(), result.getNumber(), result.getSize());
    }

    @Override
    public List<LogisticsOrder> findAll() {
        return logisticsOrderJpaRepository.findAll()
                .stream()
                .map(logisticsOrderMapper::toDomainAggregate)
                .toList();
    }

    @Override
    public long countByTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return logisticsOrderJpaRepository.count();
        }
        return logisticsOrderJpaRepository.countByTenantId(tenantId);
    }

    @Override
    public void delete(String id) {
        logisticsOrderJpaRepository.deleteById(id);
    }

    private Specification<LogisticsOrderJpaEntity>
            buildSpec(String tenantId, String trackingNumber, String orderId, String channelId,
                      LogisticsStatus status, String keyword, String startDate, String endDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (tenantId != null && !tenantId.isBlank()) {
                predicates.add(cb.equal(root.get("tenantId"), tenantId));
            }
            if (trackingNumber != null && !trackingNumber.isBlank()) {
                predicates.add(cb.like(root.get("trackingNumber"), "%" + trackingNumber.trim() + "%"));
            }
            if (orderId != null && !orderId.isBlank()) {
                predicates.add(cb.equal(root.get("orderId"), orderId.trim()));
            }
            if (channelId != null && !channelId.isBlank()) {
                predicates.add(cb.equal(root.get("logisticsChannel").get("id"), channelId.trim()));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), logisticsOrderMapper.toStatusEntity(status)));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("logisticsOrderNumber"), like),
                        cb.like(root.get("trackingNumber"), like),
                        cb.like(root.get("carrier"), like)));
            }
            Long from = toStartMillis(startDate);
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            Long to = toEndMillis(endDate);
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Long toStartMillis(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date.trim()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            return null;
        }
    }

    private static Long toEndMillis(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date.trim()).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant()
                    .toEpochMilli();
        } catch (Exception e) {
            return null;
        }
    }
}
