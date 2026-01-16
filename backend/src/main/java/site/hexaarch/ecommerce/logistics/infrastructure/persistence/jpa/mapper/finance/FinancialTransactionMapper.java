package site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.finance;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import site.hexaarch.ecommerce.logistics.domain.finance.aggregate.FinancialTransaction;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionStatus;
import site.hexaarch.ecommerce.logistics.domain.finance.valueobject.TransactionType;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.finance.FinancialTransactionJpaEntity;
import site.hexaarch.ecommerce.logistics.interfaces.dto.finance.FinancialTransactionDto;

/**
 * 财务交易映射器，用于在财务交易领域模型和JPA实体之间进行转换。
 *
 * @author kenyon
 */
@Mapper(componentModel = "spring")
public interface FinancialTransactionMapper {
    /**
     * 将财务交易领域模型转换为JPA实体
     */
    @Mapping(source = "transactionId", target = "transactionId")
    @Mapping(source = "type", target = "type", qualifiedByName = "transactionTypeToString")
    @Mapping(source = "amount", target = "amount")
    @Mapping(source = "currency", target = "currency")
    @Mapping(source = "referenceId", target = "referenceId")
    @Mapping(source = "referenceType", target = "referenceType")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "status", target = "status", qualifiedByName = "transactionStatusToString")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "updatedAt", target = "updatedAt")
    FinancialTransactionJpaEntity toJpaEntity(FinancialTransaction financialTransaction);

    /**
     * 将JPA实体转换为财务交易领域模型
     */
    @Mapping(source = "transactionId", target = "transactionId")
    @Mapping(source = "type", target = "type", qualifiedByName = "stringToTransactionType")
    @Mapping(source = "amount", target = "amount")
    @Mapping(source = "currency", target = "currency")
    @Mapping(source = "referenceId", target = "referenceId")
    @Mapping(source = "referenceType", target = "referenceType")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "status", target = "status", qualifiedByName = "stringToTransactionStatus")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "updatedAt", target = "updatedAt")
    FinancialTransaction toDomainAggregate(FinancialTransactionJpaEntity financialTransactionJpaEntity);

    /**
     * 将交易类型转换为字符串
     */
    @Named("transactionTypeToString")
    default String transactionTypeToString(TransactionType type) {
        return type.getCode();
    }

    /**
     * 将字符串转换为交易类型
     */
    @Named("stringToTransactionType")
    default TransactionType stringToTransactionType(String type) {
        for (TransactionType transactionType : TransactionType.values()) {
            if (transactionType.getCode().equals(type)) {
                return transactionType;
            }
        }
        throw new IllegalArgumentException("无效的交易类型: " + type);
    }

    /**
     * 将交易状态转换为字符串
     */
    @Named("transactionStatusToString")
    default String transactionStatusToString(TransactionStatus status) {
        return status.getCode();
    }

    /**
     * 将字符串转换为交易状态
     */
    @Named("stringToTransactionStatus")
    default TransactionStatus stringToTransactionStatus(String status) {
        for (TransactionStatus transactionStatus : TransactionStatus.values()) {
            if (transactionStatus.getCode().equals(status)) {
                return transactionStatus;
            }
        }
        throw new IllegalArgumentException("无效的交易状态: " + status);
    }

    /**
     * 将财务交易领域模型转换为DTO
     */
    FinancialTransactionDto toDto(FinancialTransaction financialTransaction);
}
