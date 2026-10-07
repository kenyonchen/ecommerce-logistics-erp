package site.hexaarch.ecommerce.logistics.interfaces.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 发货回写策略变更入参。
 * <p>
 * 收字符串而不是直接绑定枚举：绑定失败会由 Jackson 抛出一个不含候选值清单的泛化报文，
 * 手动解析才能报出「AUTO/MANUAL/OFF 之一」这种指向字段的提示。
 * </p>
 */
@Getter
@Setter
public class ShopPushBackPolicyRequest {

    @Schema(description = "发货回写策略：AUTO(发货即推平台)/MANUAL(人工确认后推)/OFF(不推)")
    private String pushBackPolicy;
}
