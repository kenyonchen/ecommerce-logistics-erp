package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.domain.customer.aggregate.Customer;
import site.hexaarch.ecommerce.logistics.domain.customer.repository.CustomerRepository;
import site.hexaarch.ecommerce.logistics.domain.customer.valueobject.CustomerStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.CustomerJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.CustomerStatusJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.CustomerMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.CustomerJpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 客户存储库实现类，用于在数据库中执行客户聚合根的CRUD操作。
 *
 * @author kenyon
 */
@Repository
public class CustomerRepositoryImpl implements CustomerRepository {

    private final CustomerJpaRepository customerJpaRepository;
    private final CustomerMapper customerMapper;

    @Autowired
    public CustomerRepositoryImpl(CustomerJpaRepository customerJpaRepository, CustomerMapper customerMapper) {
        this.customerJpaRepository = customerJpaRepository;
        this.customerMapper = customerMapper;
    }

    @Override
    public void save(Customer customer) {
        CustomerJpaEntity jpaEntity = customerMapper.toJpaEntity(customer);
        customerJpaRepository.save(jpaEntity);
    }

    @Override
    public Optional<Customer> findById(String customerId) {
        try {
            UUID id = UUID.fromString(customerId);
            return customerJpaRepository.findById(id)
                    .map(customerMapper::toDomain);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<Customer> findByStatus(CustomerStatus status) {
        CustomerStatusJpaEntity jpaStatus = CustomerStatusJpaEntity.valueOf(status.name());
        return customerJpaRepository.findAll().stream()
                .filter(entity -> entity.getStatus() == jpaStatus)
                .map(customerMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Customer> findByName(String customerName) {
        return customerJpaRepository.findAll().stream()
                .filter(entity -> entity.getName().equals(customerName))
                .map(customerMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Customer> findByEmail(String email) {
        return customerJpaRepository.findAll().stream()
                .filter(entity -> entity.getEmail().equals(email))
                .map(customerMapper::toDomain)
                .findFirst();
    }
}
