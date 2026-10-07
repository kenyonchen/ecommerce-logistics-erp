package site.hexaarch.ecommerce.logistics.interfaces.dto.platform;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthSpec;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;

import java.util.List;

/**
 * 对接协议元数据出参：配置界面据此渲染「这家要填哪几把钥匙」。
 * <p>
 * 与前端硬编码一份枚举列表相比，这张表由后端 {@code PlatformAuthCatalog} 生成，
 * 新增平台或改了必填项只需改一处，界面与校验不会漂移。
 *
 * @author kenyon
 */
@Getter
@Setter
@Schema(description = "对接协议的凭据要求与文档索引")
public class PlatformAuthSpecDto {

    private PlatformAuthType authType;
    private String label;
    @Schema(description = "必填字段（PlatformIntegrationSaveRequest 的属性名）")
    private List<String> requiredFields;
    @Schema(description = "选填字段")
    private List<String> optionalFields;
    @Schema(description = "平台 host 是否来自配置/内置常量；true 时 storeUrl 不必填")
    private boolean hostFromConfig;
    @Schema(description = "region 候选值，空表示不需要区域")
    private List<String> regionOptions;
    @Schema(description = "凭据获取步骤说明")
    private String tokenGuide;
    @Schema(description = "已核实的官方文档地址")
    private List<String> docUrls;
    @Schema(description = "接口规范是否已按公开可读的官方文档核实；false 表示没有适配器")
    private boolean verified;
    @Schema(description = "落地状态与已知缺口")
    private String note;

    public static PlatformAuthSpecDto from(PlatformAuthSpec spec) {
        PlatformAuthSpecDto dto = new PlatformAuthSpecDto();
        dto.authType = spec.type();
        dto.label = spec.label();
        dto.requiredFields = spec.requiredFields();
        dto.optionalFields = spec.optionalFields();
        dto.hostFromConfig = spec.hostFromConfig();
        dto.regionOptions = spec.regionOptions();
        dto.tokenGuide = spec.tokenGuide();
        dto.docUrls = spec.docUrls();
        dto.verified = spec.verified();
        dto.note = spec.note();
        return dto;
    }
}
