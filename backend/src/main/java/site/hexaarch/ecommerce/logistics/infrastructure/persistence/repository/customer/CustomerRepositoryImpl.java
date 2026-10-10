package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.customer;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;
import site.hexaarch.ecommerce.logistics.domain.common.BaseAggregateRoot;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.customer.aggregate.Customer;
import site.hexaarch.ecommerce.logistics.domain.customer.repository.CustomerQuery;
import site.hexaarch.ecommerce.logistics.domain.customer.repository.CustomerRepository;
import site.hexaarch.ecommerce.logistics.domain.customer.valueobject.CustomerStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.customer.CustomerJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.customer.CustomerMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.customer.CustomerJpaRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.specification.customer.CustomerSpecification;

/**
 * 客户存储库实现类，用于在数据库中执行客户聚合根的CRUD操作。
 *
 * @author kenyon
 */
@Repository
public class CustomerRepositoryImpl implements CustomerRepository {

    private final CustomerJpaRepository customerJpaRepository;
    private final CustomerMapper customerMapper;

    public CustomerRepositoryImpl(CustomerJpaRepository customerJpaRepository, CustomerMapper customerMapper) {
        this.customerJpaRepository = customerJpaRepository;
        this.customerMapper = customerMapper;
    }

    @Override
    public Customer save(Customer customer) {
        CustomerJpaEntity jpaEntity = customerMapper.toJpaEntity(customer);
        LocalDateTime now = LocalDateTime.now();
        if (jpaEntity.getCreatedAt() == null) {
            jpaEntity.setCreatedAt(now);
        }
        jpaEntity.setUpdatedAt(now);
        CustomerJpaEntity saved = customerJpaRepository.save(jpaEntity);
        Customer savedCustomer = customerMapper.toDomain(saved);
        // save 重建的聚合副本不携带领域事件，补回后应用服务才能发布（ISS-062）
        BaseAggregateRoot.carryOverEvents(customer, savedCustomer);
        return savedCustomer;
    }

    @Override
    public Optional<Customer> findById(String customerId) {
        if (!StringUtils.hasText(customerId)) {
            return Optional.empty();
        }
        return customerJpaRepository.findById(customerId.trim()).map(customerMapper::toDomain);
    }

    @Override
    public Optional<Customer> findByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return Optional.empty();
        }
        return customerJpaRepository.findByEmail(email.trim()).map(customerMapper::toDomain);
    }

    @Override
    public Optional<Customer> findByExternalBuyerId(String tenantId, String sourcePlatform, String externalBuyerId) {
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(sourcePlatform) || !StringUtils.hasText(externalBuyerId)) {
            return Optional.empty();
        }
        return customerJpaRepository
                .findByTenantIdAndSourcePlatformAndExternalBuyerId(tenantId, sourcePlatform, externalBuyerId.trim())
                .map(customerMapper::toDomain);
    }

    @Override
    public long countByStatus(CustomerStatus status) {
        return customerJpaRepository.countByStatus(status);
    }

    @Override
    public PageResult<Customer> findAll(CustomerQuery query) {
        int page = Math.max(query.page(), 0);
        int size = Math.min(Math.max(query.size(), 1), 200);
        var pageable = PageRequest.of(page, size, resolveSort(query.sort()));
        var result = customerJpaRepository.findAll(CustomerSpecification.withQuery(query), pageable);
        List<Customer> customers = new ArrayList<>(result.getNumberOfElements());
        for (CustomerJpaEntity entity : result.getContent()) {
            customers.add(customerMapper.toDomain(entity));
        }
        return new PageResult<>(customers, result.getTotalElements(), result.getNumber(), result.getSize());
    }

    private Sort resolveSort(String sort) {
        String[] parts = sort == null ? new String[0] : sort.split(",");
        String property = "createdAt";
        if (parts.length > 0 && !parts[0].isBlank()) {
            property = switch (parts[0].trim()) {
                case "customerName", "name" -> "name";
                case "updatedAt" -> "updatedAt";
                case "email" -> "email";
                default -> "createdAt";
            };
        }
        Sort.Direction direction = Sort.Direction.DESC;
        if (parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())) {
            direction = org.springframework.data.domain.Sort.Direction.ASC;
        }
        return org.springframework.data.domain.Sort.by(direction, property);
    }
}
