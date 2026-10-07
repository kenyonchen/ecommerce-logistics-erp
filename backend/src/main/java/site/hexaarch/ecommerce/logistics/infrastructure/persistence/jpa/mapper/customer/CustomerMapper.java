package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.customer;

import org.mapstruct.Mapper;
import site.hexaarch.ecommerce.logistics.domain.customer.aggregate.Customer;
import site.hexaarch.ecommerce.logistics.domain.customer.valueobject.CustomerStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.customer.CustomerJpaEntity;

/**
 * 客户映射器
 *
 * @author kenyon
 */
@Mapper(componentModel = "spring")
public interface CustomerMapper {

    default CustomerJpaEntity toJpaEntity(Customer customer) {
        if (customer == null) {
            return null;
        }
        CustomerJpaEntity entity = new CustomerJpaEntity();
        entity.setId(customer.getCustomerId());
        entity.setTenantId(customer.getTenantId());
        entity.setName(customer.getCustomerName());
        entity.setEmail(customer.getEmail());
        entity.setPhone(customer.getPhone());
        entity.setAddress(customer.getAddress());
        entity.setCompany(customer.getCompany());
        entity.setSourcePlatform(customer.getSourcePlatform());
        entity.setExternalBuyerId(customer.getExternalBuyerId());
        entity.setStatus(customer.getStatus());
        entity.setCreatedAt(customer.getCreatedAt());
        entity.setUpdatedAt(customer.getUpdatedAt());
        return entity;
    }

    /**
     * 读取还原：走 {@link Customer#restore}，不注册领域事件、不重置时间戳
     */
    default Customer toDomain(CustomerJpaEntity customerJpaEntity) {
        if (customerJpaEntity == null) {
            return null;
        }
        return Customer.restore(
                customerJpaEntity.getTenantId(),
                customerJpaEntity.getId(),
                customerJpaEntity.getName(),
                customerJpaEntity.getEmail(),
                customerJpaEntity.getPhone(),
                customerJpaEntity.getAddress(),
                customerJpaEntity.getCompany(),
                customerJpaEntity.getSourcePlatform(),
                customerJpaEntity.getExternalBuyerId(),
                customerJpaEntity.getStatus() == null ? CustomerStatus.ACTIVE : customerJpaEntity.getStatus(),
                customerJpaEntity.getCreatedAt(),
                customerJpaEntity.getUpdatedAt()
        );
    }
}
