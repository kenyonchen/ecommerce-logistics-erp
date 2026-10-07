package site.hexaarch.ecommerce.logistics.interfaces.dto.customer;

import lombok.Data;

/**
 * 更新客户资料请求（字段为空表示保持原值）
 *
 * @author kenyon
 */
@Data
public class CustomerUpdateRequest {
    private String customerName;

    private String email;

    private String phone;

    private String address;

    /**
     * 公司名（可空，传空表示保持原值）
     */
    private String company;
}
