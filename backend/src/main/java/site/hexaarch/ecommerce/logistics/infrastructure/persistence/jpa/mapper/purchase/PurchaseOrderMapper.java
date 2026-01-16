package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.purchase;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import site.hexaarch.ecommerce.logistics.domain.purchase.aggregate.PurchaseOrder;
import site.hexaarch.ecommerce.logistics.domain.purchase.entity.PurchaseOrderItem;
import site.hexaarch.ecommerce.logistics.domain.purchase.valueobject.PurchaseStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.purchase.PurchaseOrderItemJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.purchase.PurchaseOrderJpaEntity;

/**
 * 采购单映射器，用于在采购单领域模型和JPA实体之间进行转换。
 *
 * @author kenyon
 */
@Mapper(componentModel = "spring")
public interface PurchaseOrderMapper {
    /**
     * 将采购单领域模型转换为JPA实体
     */
    @Mapping(source = "purchaseOrderId", target = "purchaseOrderId")
    @Mapping(source = "status", target = "status", qualifiedByName = "purchaseStatusToString")
    PurchaseOrderJpaEntity toJpaEntity(PurchaseOrder purchaseOrder);

    /**
     * 将JPA实体转换为采购单领域模型
     */
    @Mapping(source = "purchaseOrderId", target = "purchaseOrderId")
    @Mapping(source = "status", target = "status", qualifiedByName = "stringToPurchaseStatus")
    PurchaseOrder toDomainAggregate(PurchaseOrderJpaEntity purchaseOrderJpaEntity);

    /**
     * 将采购单项领域模型转换为JPA实体
     */
    @Mapping(source = "skuCode", target = "skuCode")
    @Mapping(source = "productName", target = "productName")
    @Mapping(source = "quantity", target = "quantity")
    @Mapping(source = "unitPrice", target = "unitPrice")
    @Mapping(source = "totalPrice", target = "totalPrice")
    PurchaseOrderItemJpaEntity toJpaEntity(PurchaseOrderItem purchaseOrderItem);

    /**
     * 将JPA实体转换为采购单项领域模型
     */
    @Mapping(source = "skuCode", target = "skuCode")
    @Mapping(source = "productName", target = "productName")
    @Mapping(source = "quantity", target = "quantity")
    @Mapping(source = "unitPrice", target = "unitPrice")
    @Mapping(source = "totalPrice", target = "totalPrice")
    PurchaseOrderItem toDomainEntity(PurchaseOrderItemJpaEntity purchaseOrderItemJpaEntity);

    /**
     * 将采购状态转换为字符串
     */
    @Named("purchaseStatusToString")
    default String purchaseStatusToString(PurchaseStatus status) {
        return status.getCode();
    }

    /**
     * 将字符串转换为采购状态
     */
    @Named("stringToPurchaseStatus")
    default PurchaseStatus stringToPurchaseStatus(String status) {
        for (PurchaseStatus purchaseStatus : PurchaseStatus.values()) {
            if (purchaseStatus.getCode().equals(status)) {
                return purchaseStatus;
            }
        }
        throw new IllegalArgumentException("无效的采购状态: " + status);
    }
}
