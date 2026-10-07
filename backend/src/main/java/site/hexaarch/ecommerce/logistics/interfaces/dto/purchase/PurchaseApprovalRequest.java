package site.hexaarch.ecommerce.logistics.interfaces.dto.purchase;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 采购审批请求体（通过 / 拒绝共用）。
 *
 * @author kenyon
 */
@Getter
@Setter
@Schema(description = "采购审批入参")
public class PurchaseApprovalRequest {

    @Schema(description = "审批人，省略时取登录账号")
    private String approvedBy;

    @Schema(description = "审批意见；拒绝时作为拒绝原因，必填")
    private String comment;
}
