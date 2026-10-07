package site.hexaarch.ecommerce.logistics.interfaces.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import site.hexaarch.ecommerce.logistics.application.command.shop.CreateShopCommand;

/**
 * 新建店铺入参：只有主数据，<b>没有凭据字段</b>。
 * <p>
 * 建店与换凭据是两个风险等级（后者能把店铺指向别人的授权），因此拆成
 * {@code POST /api/shops} 与 {@code PUT /api/shops/{id}/credentials} 两个端点、两个权限点。
 * </p>
 *
 * @author kenyon
 */
@Getter
@Setter
public class ShopCreateRequest {

    @Schema(description = "对接定义ID；自授权模式可留空")
    private String integrationId;
    @Schema(description = "平台标识，如 AMAZON/SHOPEE/EBAY（与订单的 platform 同口径）")
    private String platform;
    @Schema(description = "店铺编码，租户内唯一，卖家自定，是店铺的主身份")
    private String shopCode;
    @Schema(description = "店铺名称")
    private String shopName;
    @Schema(description = "区域：Amazon 取 NA/EU/FE")
    private String region;
    @Schema(description = "站点标识：Amazon 的 marketplaceId，如 ATVPDKIKX0DER")
    private String marketplaceId;

    public CreateShopCommand toCommand() {
        return new CreateShopCommand(integrationId, platform, shopCode, shopName, region, marketplaceId);
    }
}
