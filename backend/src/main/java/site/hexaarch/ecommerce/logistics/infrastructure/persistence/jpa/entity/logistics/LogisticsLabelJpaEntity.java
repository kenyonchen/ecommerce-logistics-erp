package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.logistics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 物流标签JPA实体，用于持久化物流标签值对象。
 * <p>
 * 面单的收发件人与包裹信息以扁平列存储，保证值对象可以完整往返读写。
 * </p>
 *
 * @author kenyon
 */
@Entity
@Table(name = "logistics_labels")
@Getter
@Setter
public class LogisticsLabelJpaEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "shipper_name")
    private String shipperName;

    @Column(name = "shipper_address")
    private String shipperAddress;

    @Column(name = "shipper_phone")
    private String shipperPhone;

    @Column(name = "recipient_name")
    private String recipientName;

    @Column(name = "recipient_address")
    private String recipientAddress;

    @Column(name = "recipient_phone")
    private String recipientPhone;

    @Column(name = "package_weight")
    private Double packageWeight;

    @Column(name = "package_quantity")
    private Integer packageQuantity;

    @Column(name = "barcode")
    private String barcode;

    @Column(name = "label_data", columnDefinition = "TEXT")
    private String labelData;

    @Column(name = "label_url")
    private String labelUrl;

    @Column(name = "label_format")
    private String labelFormat;
}
