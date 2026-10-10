package site.hexaarch.ecommerce.logistics.interfaces.controller.logistics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.hexaarch.ecommerce.logistics.domain.audit.Auditable;
import site.hexaarch.ecommerce.logistics.domain.audit.AuditActions;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.logistics.entity.LogisticsChannel;
import site.hexaarch.ecommerce.logistics.domain.logistics.service.LogisticsChannelService;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsFee;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.LogisticsChannelDto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 物流渠道控制器，提供渠道维护与运费试算接口。
 * <p>
 * 渠道编码是平台物流数据导入时的匹配依据（导入按 channelCode/carrier 定位渠道），
 * 因此渠道必须可被前端维护，否则导入行会因「无法匹配物流渠道」整行失败。
 * </p>
 *
 * @author kenyon
 */
@RestController
@RequestMapping("/api/logistics-channels")
@RequiredArgsConstructor
@Tag(name = "物流渠道管理", description = "物流渠道的增删改查与运费试算")
public class LogisticsChannelController {
    private static final String HAS_LOGISTICS_READ =
            "hasRole('PLATFORM_ADMIN') or hasAuthority('logistics:read')";
    private static final String HAS_LOGISTICS_WRITE =
            "hasRole('PLATFORM_ADMIN') or hasAuthority('logistics:write')";


    private final LogisticsChannelService logisticsChannelService;

    @Operation(summary = "查询渠道列表", description = "country 为空返回全部渠道；指定 country 时返回该地区可用渠道")
    @PreAuthorize(HAS_LOGISTICS_READ)
    @GetMapping
    public Result<List<LogisticsChannelDto>> listChannels(
            @Parameter(description = "国家/地区代码") @RequestParam(required = false) String country,
            @Parameter(description = "仅返回启用渠道") @RequestParam(required = false, defaultValue = "false")
            Boolean onlyActive) {
        List<LogisticsChannel> channels = Boolean.TRUE.equals(onlyActive)
                ? logisticsChannelService.getAvailableChannels(country)
                : logisticsChannelService.findAll();
        return Result.success(channels.stream().map(LogisticsChannelDto::from).toList());
    }

    @Operation(summary = "根据ID获取渠道", description = "获取物流渠道详细信息")
    @PreAuthorize(HAS_LOGISTICS_READ)
    @GetMapping("/{id}")
    public Result<LogisticsChannelDto> getChannel(@Parameter(description = "渠道ID") @PathVariable String id) {
        return Result.success(logisticsChannelService.findById(id)
                .map(LogisticsChannelDto::from)
                .orElseThrow(() -> new EntityNotFoundException("物流渠道不存在: " + id)));
    }

    @Operation(summary = "创建渠道", description = "新增物流渠道，渠道编码不可重复")
    @PreAuthorize(HAS_LOGISTICS_WRITE)
    @Auditable(action = AuditActions.LOGISTICS_CHANNEL_CREATE, label = "创建物流渠道",
            targetType = "LogisticsChannel", targetIdResult = "channelId")
    @PostMapping
    public Result<LogisticsChannelDto> createChannel(@RequestBody LogisticsChannelDto request) {
        if (request.getChannelCode() == null || request.getChannelCode().isBlank()) {
            throw new ValidationException("渠道编码不能为空");
        }
        LogisticsChannel channel = LogisticsChannel.create(request.getChannelCode().trim(), request.getChannelName(),
                request.getDescription(), request.getCountries(), request.getBasePrice(), request.getPricePerGram(),
                request.getCurrency(), request.getActive() == null || request.getActive());
        return Result.success(LogisticsChannelDto.from(logisticsChannelService.createLogisticsChannel(channel)));
    }

    @Operation(summary = "更新渠道", description = "更新物流渠道基础信息与计费规则")
    @PreAuthorize(HAS_LOGISTICS_WRITE)
    @Auditable(action = AuditActions.LOGISTICS_CHANNEL_UPDATE, label = "更新物流渠道", targetType = "LogisticsChannel")
    @PutMapping("/{id}")
    public Result<LogisticsChannelDto> updateChannel(
            @Parameter(description = "渠道ID") @PathVariable String id,
            @RequestBody LogisticsChannelDto request) {
        LogisticsChannel existing = logisticsChannelService.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("物流渠道不存在: " + id));
        boolean active = request.getActive() == null ? existing.isActive() : request.getActive();
        LogisticsChannel updated = existing.toBuilder()
                .channelCode(request.getChannelCode() == null ? existing.getChannelCode()
                        : request.getChannelCode().trim())
                .channelName(request.getChannelName() == null ? existing.getChannelName() : request.getChannelName())
                .description(request.getDescription() == null ? existing.getDescription() : request.getDescription())
                .countries(request.getCountries() == null ? existing.getCountries() : request.getCountries())
                .basePrice(request.getBasePrice() == null ? existing.getBasePrice() : request.getBasePrice())
                .pricePerGram(request.getPricePerGram() == null ? existing.getPricePerGram()
                        : request.getPricePerGram())
                .currency(request.getCurrency() == null ? existing.getCurrency() : request.getCurrency())
                .active(active)
                .build();
        return Result.success(LogisticsChannelDto.from(logisticsChannelService.updateLogisticsChannel(updated)));
    }

    @Operation(summary = "启用/停用渠道", description = "变更渠道启用状态")
    @PreAuthorize(HAS_LOGISTICS_WRITE)
    @Auditable(action = AuditActions.LOGISTICS_CHANNEL_STATUS_UPDATE, label = "变更物流渠道状态", targetType = "LogisticsChannel")
    @PutMapping("/{id}/status")
    public Result<LogisticsChannelDto> changeStatus(
            @Parameter(description = "渠道ID") @PathVariable String id,
            @Parameter(description = "是否启用") @RequestParam boolean active) {
        return Result.success(LogisticsChannelDto.from(logisticsChannelService.changeChannelStatus(id, active)));
    }

    @Operation(summary = "删除渠道", description = "删除渠道（存在关联物流单时禁止删除）")
    @PreAuthorize(HAS_LOGISTICS_WRITE)
    @Auditable(action = AuditActions.LOGISTICS_CHANNEL_DELETE, label = "删除物流渠道", targetType = "LogisticsChannel")
    @DeleteMapping("/{id}")
    public Result<Void> deleteChannel(@Parameter(description = "渠道ID") @PathVariable String id) {
        logisticsChannelService.deleteLogisticsChannel(id);
        return Result.success(null);
    }

    @Operation(summary = "运费试算", description = "按渠道首重费用与续重单价试算运费，weight 单位为克")
    @PreAuthorize(HAS_LOGISTICS_READ)
    @GetMapping("/{id}/fee")
    public Result<LogisticsFee> calculateFee(
            @Parameter(description = "渠道ID") @PathVariable String id,
            @Parameter(description = "重量（克）") @RequestParam BigDecimal weight,
            @Parameter(description = "国家/地区代码") @RequestParam(required = false) String country) {
        double grams = weight == null ? 0D : weight.doubleValue();
        return Result.success(logisticsChannelService.calculateFee(id, grams, country));
    }
}
