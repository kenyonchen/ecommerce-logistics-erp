package site.hexaarch.ecommerce.logistics.application.command.shop;

/**
 * 新建店铺命令（主数据，不含任何凭据字段）。
 * <p>
 * 凭据走 {@link ShopCredentialCommand} 的专用通道：改店铺名与换 token 的风险等级不同
 * （后者能把店铺指向别人的授权），因此不给同一个接口同时开放。ADR-0013 §4.1。
 * </p>
 *
 * @param integrationId 对接定义ID，可空（自授权模式）
 * @param platform      平台标识
 * @param shopCode      租户内唯一的店铺编码（卖家自定，店铺主身份）
 * @param shopName      店铺名称
 * @param region        区域，如 Amazon 的 NA/EU/FE
 * @param marketplaceId 站点标识，如 Amazon 的 ATVPDKIKX0DER
 */
public record CreateShopCommand(
        String integrationId,
        String platform,
        String shopCode,
        String shopName,
        String region,
        String marketplaceId
) {
}
