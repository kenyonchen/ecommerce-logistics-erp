package site.hexaarch.ecommerce.logistics.interfaces.dto.customer;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户信息DTO
 *
 * @author kenyon
 */
@Data
public class CustomerDto {
    private String customerId;
    private String tenantId;
    private String customerName;
    private String email;
    private String phone;
    private String address;
    /**
     * 公司名（跨境B2B买家）
     */
    private String company;
    /**
     * 客户状态：ACTIVE/INACTIVE/SUSPENDED/DELETED
     */
    private String status;
    private String statusLabel;
    /**
     * 来源平台（第三方电商平台名称），手动创建时为空
     */
    private String sourcePlatform;
    /**
     * 平台侧买家ID
     */
    private String externalBuyerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 状态下拉选项
     */
    public record StatusOption(String code, String label) {
    }
}
