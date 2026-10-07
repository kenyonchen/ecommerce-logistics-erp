package site.hexaarch.ecommerce.logistics.interfaces.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 店铺主数据更新入参。
 * <p>
 * 本类<b>结构上就不含</b>凭据字段：{@code PUT /api/shops/{id}} 由 {@code shop:write} 守护，
 * 而写凭据需要 {@code shop:credential_write}。把凭据挡在类型之外，比在方法里「记得忽略这几个字段」可靠。
 * </p>
 * <p>
 * 店铺编码也不可改：它是租户内的身份锚，改它等于换店。
 * </p>
 */
@Getter
@Setter
public class ShopUpdateRequest {

    @Schema(description = "店铺名称，留空表示不改")
    private String shopName;
    @Schema(description = "启用/停用，留空表示不改")
    private Boolean active;
}
