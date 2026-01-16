package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import site.hexaarch.ecommerce.logistics.domain.customer.aggregate.Customer;
import site.hexaarch.ecommerce.logistics.domain.customer.valueobject.CustomerStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.CustomerJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.CustomerStatusJpaEntity;

/**
 * 客户映射器
 *
 * @author kenyon
 */
@Mapper(componentModel = "spring")
public interface CustomerMapper {

    @Mapping(source = "customerId", target = "id")
    @Mapping(source = "customerName", target = "name")
    @Mapping(source = "status", target = "status", qualifiedByName = "customerStatusToJpaEntity")
    CustomerJpaEntity toJpaEntity(Customer customer);

    default Customer toDomain(CustomerJpaEntity customerJpaEntity) {
        if (customerJpaEntity == null) {
            return null;
        }
        // Use the static create method instead of constructor
        Customer customer = Customer.create(
                "default-tenant",
                customerJpaEntity.getId().toString(),
                customerJpaEntity.getName(),
                customerJpaEntity.getEmail(),
                customerJpaEntity.getPhone(),
                ""
        );

        // Update status if it's not ACTIVE (which is the default from create())
        if (customerJpaEntity.getStatus() != CustomerStatusJpaEntity.ACTIVE) {
            switch (customerJpaEntity.getStatus()) {
                case INACTIVE:
                    customer.deactivate();
                    break;
                case SUSPENDED:
                    customer.suspend();
                    break;
                case DELETED:
                    customer.delete();
                    break;
            }
        }

        return customer;
    }

    @Named("customerStatusToJpaEntity")
    default CustomerStatusJpaEntity customerStatusToJpaEntity(CustomerStatus status) {
        if (status == null) {
            return null;
        }
        return CustomerStatusJpaEntity.valueOf(status.name());
    }

    @Named("jpaEntityToCustomerStatus")
    default CustomerStatus jpaEntityToCustomerStatus(CustomerStatusJpaEntity status) {
        if (status == null) {
            return null;
        }
        return CustomerStatus.valueOf(status.name());
    }
}
