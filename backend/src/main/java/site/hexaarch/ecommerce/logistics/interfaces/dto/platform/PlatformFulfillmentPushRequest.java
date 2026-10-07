package site.hexaarch.ecommerce.logistics.interfaces.dto.platform;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 发货回写入参：指定要回写到平台的本地物流单。
 *
 * @author kenyon
 */
@Getter
@Setter
public class PlatformFulfillmentPushRequest {

    @Schema(description = "本地物流单ID，必须是已标记发货且已绑定运单号的物流单")
    private String logisticsOrderId;

    @Schema(description = "是否让平台给买家发送发货通知（Shopify 的 notify_customer）", defaultValue = "false")
    private boolean notifyCustomer;
}
