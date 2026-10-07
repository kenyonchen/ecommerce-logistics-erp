package site.hexaarch.ecommerce.logistics.interfaces.dto.customer;

import lombok.Data;

/**
 * 创建客户请求（校验在应用/领域服务层完成，与商品模块约定一致）
 *
 * @author kenyon
 */
@Data
public class CustomerCreateRequest {
    /**
     * 租户ID，缺省为 default-tenant
     */
    private String tenantId;

    private String customerName;

    private String email;

    private String phone;

    private String address;

    /**
     * 公司名（可空，跨境B2B买家）
     */
    private String company;
}
