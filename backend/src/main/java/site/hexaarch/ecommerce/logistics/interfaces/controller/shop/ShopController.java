package site.hexaarch.ecommerce.logistics.interfaces.controller.shop;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.hexaarch.ecommerce.logistics.application.service.shop.ShopApplicationService;
import site.hexaarch.ecommerce.logistics.domain.audit.Auditable;
import site.hexaarch.ecommerce.logistics.domain.audit.AuditActions;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.shop.valueobject.PushBackPolicy;
import site.hexaarch.ecommerce.logistics.domain.tenant.RoleNames;
import site.hexaarch.ecommerce.logistics.infrastructure.security.TenantContext;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;
import site.hexaarch.ecommerce.logistics.interfaces.controller.platform.PlatformIntegrationController;
import site.hexaarch.ecommerce.logistics.interfaces.dto.shop.ShopCreateRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.shop.ShopCredentialRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.shop.ShopDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.shop.ShopPushBackPolicyRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.shop.ShopUpdateRequest;

import java.util.List;
import java.util.Set;

/**
 * 店铺控制器：租户在第三方平台站点上的经营实体与其凭据绑定。
 * <p>
 * 权限按 ADR-0013 §4.2 的端点×权限矩阵逐方法声明，四个租户级权限点各管一段：
 * <ul>
 *   <li>{@code shop:read} —— 列表/详情（出参恒为脱敏）；</li>
 *   <li>{@code shop:write} —— 建店、改主数据、启停、改发货回写策略、删除；</li>
 *   <li>{@code shop:credential_write} —— 只有这一个端点能写凭据列；</li>
 *   <li>{@code shop:sync} —— 外呼类（连通性测试/拉单/回写），随凭据切流一并开放，见下。</li>
 * </ul>
 * 之所以不把 {@code shop:credential_write} 并进 {@code shop:write}：合并后运营改个店名就能顺手把
 * 店铺指向别人的 token，等于用受害者配额拉回攻击者店铺的数据并污染订单归属（§4.1）。
 * </p>
 * <p>
 * <b>租户范围</b>：{@code tenantId} 对平台管理员同样必填（§4.4）。平台管理员在
 * {@code TenantIsolationAspect} 里走提前 return 的旁路，所以这里必须显式解析并要求指定租户，
 * 不提供「不传就全量」的口子——那正是 {@code PlatformIntegrationController#getAllPlatformIntegrations}
 * 的形状，本类禁止照抄。
 * </p>
 * <p>
 * 待办：{@code POST /{id}/test-connection}、{@code /{id}/sync-orders}、{@code /{id}/push-fulfillment}
 * 依赖外呼凭据从 {@code platform_integrations} 切流到 {@code shops}（§6 步骤 5），本轮未开半实现端点。
 * </p>
 *
 * @author kenyon
 */
@RestController
@RequestMapping("/api/shops")
@Tag(name = "店铺管理", description = "租户店铺与平台站点绑定、凭据配置")
public class ShopController {

    private static final String HAS_SHOP_READ =
            "hasRole('PLATFORM_ADMIN') or hasAuthority('shop:read')";
    private static final String HAS_SHOP_WRITE =
            "hasRole('PLATFORM_ADMIN') or hasAuthority('shop:write')";
    private static final String HAS_SHOP_CREDENTIAL_WRITE =
            "hasRole('PLATFORM_ADMIN') or hasAuthority('shop:credential_write')";

    private static final String ROLE_PLATFORM_ADMIN = "ROLE_" + RoleNames.PLATFORM_ADMIN;

    private final ShopApplicationService shopApplicationService;

    public ShopController(ShopApplicationService shopApplicationService) {
        this.shopApplicationService = shopApplicationService;
    }

    @Operation(summary = "店铺列表", description = "返回指定租户的店铺；出参凭据只给掩码与是否已配置位。"
            + "租户用户忽略 tenantId 入参、强制取登录租户；平台管理员必须显式指定租户")
    @PreAuthorize(HAS_SHOP_READ)
    @GetMapping
    public Result<List<ShopDto>> listShops(
            @Parameter(description = "租户ID，平台管理员必填") @RequestParam(required = false) String tenantId,
            @Parameter(description = "平台过滤") @RequestParam(required = false) String platform) {
        String scope = resolveTenantScope(tenantId);
        return Result.success(shopApplicationService.listShops(scope, platform).stream()
                .map(ShopDto::from)
                .toList());
    }

    @Operation(summary = "店铺详情", description = "按ID查询本租户店铺；跨租户或不存在均返回 404，"
            + "不通过状态码差异确认他租户资源存在性")
    @PreAuthorize(HAS_SHOP_READ)
    @GetMapping("/{id}")
    public Result<ShopDto> getShop(
            @Parameter(description = "租户ID，平台管理员必填") @RequestParam(required = false) String tenantId,
            @Parameter(description = "店铺ID") @PathVariable String id) {
        return Result.success(ShopDto.from(shopApplicationService.getShop(resolveTenantScope(tenantId), id)));
    }

    @Operation(summary = "新建店铺", description = "只创建主数据，凭据列为空、授权状态 PENDING；"
            + "凭据请用 PUT /api/shops/{id}/credentials")
    @PreAuthorize(HAS_SHOP_WRITE)
    @PostMapping
    @Auditable(action = AuditActions.SHOP_CREATE, label = "新建店铺")
    public Result<ShopDto> createShop(
            @Parameter(description = "租户ID，平台管理员必填") @RequestParam(required = false) String tenantId,
            @RequestBody ShopCreateRequest request) {
        String scope = resolveTenantScope(tenantId);
        return Result.success(ShopDto.from(shopApplicationService.createShop(scope, request.toCommand())));
    }

    @Operation(summary = "更新店铺主数据", description = "改店铺名与启停。本接口结构上不含凭据字段，"
            + "店铺编码也不可改（改编码等于换店）")
    @PreAuthorize(HAS_SHOP_WRITE)
    @PutMapping("/{id}")
    @Auditable(action = AuditActions.SHOP_UPDATE, label = "更新店铺主数据")
    public Result<ShopDto> updateShop(
            @Parameter(description = "租户ID，平台管理员必填") @RequestParam(required = false) String tenantId,
            @Parameter(description = "店铺ID") @PathVariable String id,
            @RequestBody ShopUpdateRequest request) {
        String scope = resolveTenantScope(tenantId);
        return Result.success(ShopDto.from(
                shopApplicationService.updateShop(scope, id, request.getShopName(), request.getActive())));
    }

    @Operation(summary = "写入店铺凭据", description = "唯一能写凭据列的端点。不填=保留原值，填空串=清除；"
            + "任何写入都会把授权状态打回 PENDING，需重跑连通性测试。回显只有掩码，不含提交值")
    @PreAuthorize(HAS_SHOP_CREDENTIAL_WRITE)
    @PutMapping("/{id}/credentials")
    @Auditable(action = AuditActions.SHOP_CREDENTIAL_UPDATE, label = "更新店铺平台凭据")
    public Result<ShopDto> bindCredentials(
            @Parameter(description = "租户ID，平台管理员必填") @RequestParam(required = false) String tenantId,
            @Parameter(description = "店铺ID") @PathVariable String id,
            @RequestBody ShopCredentialRequest request) {
        String scope = resolveTenantScope(tenantId);
        return Result.success(ShopDto.from(shopApplicationService.bindCredentials(scope, id, request.toCommand())));
    }

    @Operation(summary = "修改发货回写策略", description = "AUTO/MANUAL/OFF。只影响该店铺后续发货的推送方式，"
            + "不重写物流单上已有的单级推送状态")
    @PreAuthorize(HAS_SHOP_WRITE)
    @PutMapping("/{id}/push-back-policy")
    @Auditable(action = AuditActions.SHOP_PUSHBACK_POLICY_UPDATE, label = "修改店铺发货回写策略")
    public Result<ShopDto> changePushBackPolicy(
            @Parameter(description = "租户ID，平台管理员必填") @RequestParam(required = false) String tenantId,
            @Parameter(description = "店铺ID") @PathVariable String id,
            @RequestBody ShopPushBackPolicyRequest request) {
        String scope = resolveTenantScope(tenantId);
        return Result.success(ShopDto.from(
                shopApplicationService.changePushBackPolicy(scope, id, parsePolicy(request.getPushBackPolicy()))));
    }

    @Operation(summary = "删除店铺", description = "按租户删除店铺绑定")
    @PreAuthorize(HAS_SHOP_WRITE)
    @DeleteMapping("/{id}")
    @Auditable(action = AuditActions.SHOP_DELETE, label = "删除店铺")
    public Result<Void> deleteShop(
            @Parameter(description = "租户ID，平台管理员必填") @RequestParam(required = false) String tenantId,
            @Parameter(description = "店铺ID") @PathVariable String id) {
        shopApplicationService.deleteShop(resolveTenantScope(tenantId), id);
        return Result.success();
    }

    /**
     * 解析租户范围：
     * <ul>
     *   <li>平台管理员：必须显式指定 {@code tenantId}。切面对其提前 return、不做校验，
     *       「全量」在店铺域不是合法请求（ADR-0013 §4.4）。</li>
     *   <li>租户用户：忽略入参，强制登录租户，杜绝横向越权。</li>
     * </ul>
     */
    private String resolveTenantScope(String requestedTenantId) {
        if (isPlatformAdmin()) {
            String requested = TenantContext.normalizeRequested(requestedTenantId);
            if (requested == null) {
                throw new ValidationException("平台管理员操作店铺数据必须显式指定 tenantId");
            }
            return requested;
        }
        return TenantContext.hasTenantId() ? TenantContext.getTenantId() : "";
    }

    private PushBackPolicy parsePolicy(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("发货回写策略(pushBackPolicy)不能为空，取值 AUTO/MANUAL/OFF");
        }
        try {
            return PushBackPolicy.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("发货回写策略只能是 AUTO/MANUAL/OFF 之一，当前值: " + raw);
        }
    }

    private boolean isPlatformAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        Set<String> authorities = AuthorityUtils.authorityListToSet(authentication.getAuthorities());
        return authorities.contains(ROLE_PLATFORM_ADMIN);
    }
}
