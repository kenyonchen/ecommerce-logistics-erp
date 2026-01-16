package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 收货地址JPA实体，用于持久化收货地址值对象。
 *
 * @author kenyon
 */
@Entity
@Table(name = "shipping_addresses")
@Getter
@Setter
public class ShippingAddressJpaEntity {
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(name = "address_line1", nullable = false)
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "state_province")
    private String stateProvince;

    @Column(name = "postal_code", nullable = false)
    private String postalCode;

    @Column(name = "country", nullable = false)
    private String country;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;
}
