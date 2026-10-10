package site.hexaarch.ecommerce.logistics.interfaces.controller.platform;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import site.hexaarch.ecommerce.logistics.application.service.platform.PlatformIntegrationApplicationService;
import site.hexaarch.ecommerce.logistics.domain.audit.Auditable;
import site.hexaarch.ecommerce.logistics.domain.audit.AuditActions;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.aggregate.PlatformIntegration;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformConnectionTestResult;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformFulfillmentPusher.PlatformFulfillmentResult;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthCatalog;
import site.hexaarch.ecommerce.logistics.domain.tenant.RoleNames;
import site.hexaarch.ecommerce.logistics.domain.tenant.repository.TenantRepository;
import site.hexaarch.ecommerce.logistics.application.service.support.TenantAccessGuard;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.infrastructure.security.TenantContext;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;
import site.hexaarch.ecommerce.logistics.interfaces.dto.customerimport.CustomerImportJobDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logisticsimport.LogisticsImportJobDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.orderimport.OrderImportJobDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.platform.PlatformAuthSpecDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.platform.PlatformFulfillmentPushRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.platform.PlatformIntegrationDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.platform.PlatformIntegrationSaveRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.ProductImportJobDto;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 平台集成控制器，用于处理平台集成相关的HTTP请求。
 * <p>
 * 鉴权分两类（原先是类级统一门禁，现按读写拆分）：
 * <ul>
 *   <li><b>读</b>（{@code /auth-specs}、列表、详情）：任何登录用户即可，门槛由
 *       {@code SecurityConfig} 的 {@code /api/** authenticated()} 提供；租户侧读取按登录租户过滤，
 *       平台集成出参只含凭据掩码与「是否已配置」标志，不回传明文。</li>
 *   <li><b>写</b>（创建/更新/删除/启停/各类同步/连通性测试/发货回写）：方法级
 *       {@code @PreAuthorize} 保留 {@code platform:manage} 或 {@code ROLE_PLATFORM_ADMIN}。</li>
 * </ul>
 *
 * @author kenyon
 */
@RestController
@RequestMapping("/api/platform-integrations")
@Tag(name = "平台集成管理", description = "平台集成相关的API接口")
public class PlatformIntegrationController {

    /**
     * 写操作的统一门槛：平台管理员或持有 platform:manage 权限点的账号。
     */
    private static final String HAS_PLATFORM_MANAGE =
            "hasRole('PLATFORM_ADMIN') or hasAuthority('platform:manage')";

    private static final String ROLE_PLATFORM_ADMIN = "ROLE_" + RoleNames.PLATFORM_ADMIN;

    private final PlatformIntegrationApplicationService platformIntegrationApplicationService;
    private final TenantRepository tenantRepository;

    @Autowired
    public PlatformIntegrationController(PlatformIntegrationApplicationService platformIntegrationApplicationService,
                                        TenantRepository tenantRepository) {
        this.platformIntegrationApplicationService = platformIntegrationApplicationService;
        this.tenantRepository = tenantRepository;
    }

    @Operation(summary = "对接协议凭据要求", description = "返回各 authType 需要哪些凭据字段、region 取值、令牌获取步骤与官方文档链接；"
            + "verified=false 表示该协议的接口规范未能从公开官方文档核实，因此没有适配器。"
            + "纯静态目录不读库，任何登录用户可读，供租户端创建页渲染下拉")
    @GetMapping("/auth-specs")
    public Result<List<PlatformAuthSpecDto>> authSpecs() {
        return Result.success(PlatformAuthCatalog.all().stream().map(PlatformAuthSpecDto::from).toList());
    }

    @Operation(summary = "创建平台集成", description = "创建一个新的平台集成；凭据加密存储，出参只返回掩码")
    @PreAuthorize(HAS_PLATFORM_MANAGE)
    @PostMapping
    @Auditable(action = AuditActions.PLATFORM_INTEGRATION_CREATE, label = "创建平台集成")
    public ResponseEntity<Result<PlatformIntegrationDto>> createPlatformIntegration(
            @Parameter(description = "平台集成信息") @RequestBody PlatformIntegrationSaveRequest request) {
        try {
            PlatformIntegration created = platformIntegrationApplicationService
                    .createPlatformIntegration(toAggregate(request));
            return ResponseEntity.ok(Result.success(PlatformIntegrationDto.from(created)));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("平台集成创建失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "根据ID获取平台集成", description = "根据平台集成ID获取详细信息（凭据以掩码返回）；"
            + "租户用户只能读取本租户的平台集成，跨租户访问返回 403 TENANT_ACCESS_DENIED")
    @GetMapping("/{id}")
    public ResponseEntity<Result<PlatformIntegrationDto>> getPlatformIntegrationById(
            @Parameter(description = "平台集成ID") @PathVariable UUID id) {
        PlatformIntegration platform;
        try {
            platform = platformIntegrationApplicationService.getPlatformIntegrationById(id);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("平台集成查询失败: " + e.getMessage()));
        }
        if (platform == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.fail("平台集成不存在"));
        }
        // 租户归属校验放在 try 外，避免被上面的兜底 catch 吞成 500
        try {
            assertTenantVisible(platform);
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Result.error(e.getErrorCode(), e.getMessage()));
        }
        return ResponseEntity.ok(Result.success(PlatformIntegrationDto.from(platform)));
    }

    @Operation(summary = "获取所有平台集成", description = "获取平台集成列表。"
            + "租户用户只返回本租户数据（忽略 tenantId 入参）；平台管理员不传 tenantId 时返回全量，传则按该租户过滤")
    @GetMapping
    public ResponseEntity<Result<List<PlatformIntegrationDto>>> getAllPlatformIntegrations(
            @Parameter(description = "租户ID，仅平台管理员可用") @RequestParam(required = false) String tenantId) {
        try {
            String effectiveTenantId = resolveTenantScope(tenantId);
            List<PlatformIntegration> platforms = effectiveTenantId == null
                    ? platformIntegrationApplicationService.getAllPlatformIntegrations()
                    : platformIntegrationApplicationService.getPlatformIntegrationsByTenant(effectiveTenantId);
            return ResponseEntity.ok(Result.success(platforms.stream()
                    .map(PlatformIntegrationDto::from)
                    .collect(Collectors.toList())));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("平台集成列表查询失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "更新平台集成", description = "根据ID更新平台集成信息；凭据留空表示保留原值")
    @PreAuthorize(HAS_PLATFORM_MANAGE)
    @PutMapping("/{id}")
    @Auditable(action = AuditActions.PLATFORM_INTEGRATION_UPDATE, label = "更新平台集成")
    public ResponseEntity<Result<PlatformIntegrationDto>> updatePlatformIntegration(
            @Parameter(description = "平台集成ID") @PathVariable UUID id,
            @Parameter(description = "平台集成信息") @RequestBody PlatformIntegrationSaveRequest request) {
        // 归属校验放在 try 之前：EntityNotFoundException 是 BusinessException 的子类，
        // 放进 try 就会被下面的 catch 压成 400，与其余按 ID 写接口的 404 口径不一致（ISS-122）
        PlatformIntegration existing = requireOwnedIntegration(id);
        try {
            PlatformIntegration updated = platformIntegrationApplicationService
                    .updatePlatformIntegration(id, toAggregateForUpdate(request, existing));
            if (updated != null) {
                return ResponseEntity.ok(Result.success(PlatformIntegrationDto.from(updated)));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Result.fail("平台集成不存在"));
            }
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("平台集成更新失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "删除平台集成", description = "根据ID删除平台集成")
    @PreAuthorize(HAS_PLATFORM_MANAGE)
    @DeleteMapping("/{id}")
    @Auditable(action = AuditActions.PLATFORM_INTEGRATION_DELETE, label = "删除平台集成")
    public ResponseEntity<Result<Void>> deletePlatformIntegration(@Parameter(description = "平台集成ID") @PathVariable UUID id) {
        requireOwnedIntegration(id);
        try {
            platformIntegrationApplicationService.deletePlatformIntegration(id);
            return ResponseEntity.ok(Result.success(null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("平台集成删除失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "激活平台集成", description = "激活指定ID的平台集成")
    @PreAuthorize(HAS_PLATFORM_MANAGE)
    @PostMapping("/{id}/activate")
    @Auditable(action = AuditActions.PLATFORM_INTEGRATION_STATUS_UPDATE, label = "激活平台集成", targetType = "PlatformIntegration")
    public ResponseEntity<Result<PlatformIntegrationDto>> activatePlatformIntegration(@Parameter(description = "平台集成ID") @PathVariable UUID id) {
        requireOwnedIntegration(id);
        try {
            PlatformIntegration activated = platformIntegrationApplicationService.activatePlatformIntegration(id);
            if (activated != null) {
                return ResponseEntity.ok(Result.success(PlatformIntegrationDto.from(activated)));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Result.fail("平台集成不存在"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("平台集成激活失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "停用平台集成", description = "停用指定ID的平台集成")
    @PreAuthorize(HAS_PLATFORM_MANAGE)
    @PostMapping("/{id}/deactivate")
    @Auditable(action = AuditActions.PLATFORM_INTEGRATION_STATUS_UPDATE, label = "停用平台集成", targetType = "PlatformIntegration")
    public ResponseEntity<Result<PlatformIntegrationDto>> deactivatePlatformIntegration(@Parameter(description = "平台集成ID") @PathVariable UUID id) {
        requireOwnedIntegration(id);
        try {
            PlatformIntegration deactivated = platformIntegrationApplicationService.deactivatePlatformIntegration(id);
            if (deactivated != null) {
                return ResponseEntity.ok(Result.success(PlatformIntegrationDto.from(deactivated)));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Result.fail("平台集成不存在"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("平台集成停用失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "同步订单", description = "从平台拉取订单并完成批量导入，返回导入任务结果")
    @PreAuthorize(HAS_PLATFORM_MANAGE)
    @Auditable(action = AuditActions.PLATFORM_INTEGRATION_SYNC, label = "从平台同步订单", targetType = "PlatformIntegration")
    @PostMapping("/{id}/sync-orders")
    public ResponseEntity<Result<OrderImportJobDto>> syncOrdersFromPlatform(
            @Parameter(description = "平台集成ID") @PathVariable UUID id,
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @Parameter(description = "指定平台订单号") @RequestParam(required = false) String orderNumber,
            @Parameter(description = "增量拉取：平台侧更新时间起(yyyy-MM-dd HH:mm:ss)")
            @RequestParam(required = false) String updatedFrom,
            @Parameter(description = "增量拉取：平台侧更新时间止(yyyy-MM-dd HH:mm:ss)")
            @RequestParam(required = false) String updatedTo,
            @Parameter(description = "拉取条数") @RequestParam(required = false) Integer limit,
            @Parameter(description = "是否使用平台示例数据") @RequestParam(required = false) Boolean useSampleData) {
        requireOwnedIntegration(id);
        try {
            OrderImportJobDto result = platformIntegrationApplicationService.syncOrdersFromPlatform(id, tenantId,
                    orderNumber, updatedFrom, updatedTo, limit, useSampleData);
            return ResponseEntity.ok(Result.success(result));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("订单同步失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "同步客户（买家）", description = "从平台拉取买家（或从订单数据萃取买家）并完成批量导入，返回导入任务结果")
    @PreAuthorize(HAS_PLATFORM_MANAGE)
    @Auditable(action = AuditActions.PLATFORM_INTEGRATION_SYNC, label = "从平台同步客户", targetType = "PlatformIntegration")
    @PostMapping("/{id}/sync-customers")
    public ResponseEntity<Result<CustomerImportJobDto>> syncCustomersFromPlatform(
            @Parameter(description = "平台集成ID") @PathVariable UUID id,
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @Parameter(description = "数据来源：CUSTOMERS(平台买家接口)|ORDERS(从订单萃取买家)，默认CUSTOMERS")
            @RequestParam(required = false) String sourceType,
            @Parameter(description = "买家关键字过滤") @RequestParam(required = false) String keyword,
            @Parameter(description = "拉取条数") @RequestParam(required = false) Integer limit,
            @Parameter(description = "是否使用平台示例数据") @RequestParam(required = false) Boolean useSampleData) {
        requireOwnedIntegration(id);
        try {
            CustomerImportJobDto result = platformIntegrationApplicationService.syncCustomersFromPlatform(id, tenantId,
                    sourceType, keyword, limit, useSampleData);
            return ResponseEntity.ok(Result.success(result));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("客户同步失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "同步产品", description = "从平台拉取商品并完成批量导入，返回导入任务结果")
    @PreAuthorize(HAS_PLATFORM_MANAGE)
    @Auditable(action = AuditActions.PLATFORM_INTEGRATION_SYNC, label = "从平台同步商品", targetType = "PlatformIntegration")
    @PostMapping("/{id}/sync-products")
    public ResponseEntity<Result<ProductImportJobDto>> syncProductsFromPlatform(
            @Parameter(description = "平台集成ID") @PathVariable UUID id,
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId) {
        requireOwnedIntegration(id);
        try {
            ProductImportJobDto result = platformIntegrationApplicationService.syncProductsFromPlatform(id, tenantId);
            return ResponseEntity.ok(Result.success(result));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("产品同步失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "同步库存", description = "汇总系统内库存并推送到平台店铺，返回推送的SKU明细")
    @PreAuthorize(HAS_PLATFORM_MANAGE)
    @Auditable(action = AuditActions.PLATFORM_INTEGRATION_SYNC, label = "向平台推送库存", targetType = "PlatformIntegration")
    @PostMapping("/{id}/sync-inventory")
    public ResponseEntity<Result<Object>> syncInventoryToPlatform(@Parameter(description = "平台集成ID") @PathVariable UUID id) {
        requireOwnedIntegration(id);
        try {
            return ResponseEntity.ok(Result.success(platformIntegrationApplicationService.syncInventoryToPlatform(id)));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("库存同步失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "同步物流", description = "从平台拉取店铺物流信息并完成批量导入，返回导入任务结果（含每行成功/跳过/失败明细）")
    @PreAuthorize(HAS_PLATFORM_MANAGE)
    @Auditable(action = AuditActions.PLATFORM_INTEGRATION_SYNC, label = "从平台同步物流单", targetType = "PlatformIntegration")
    @PostMapping("/{id}/sync-logistics")
    public ResponseEntity<Result<LogisticsImportJobDto>> syncLogisticsFromPlatform(
            @Parameter(description = "平台集成ID") @PathVariable UUID id,
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @Parameter(description = "指定平台订单号") @RequestParam(required = false) String orderNumber,
            @Parameter(description = "指定运单号") @RequestParam(required = false) String trackingNumber,
            @Parameter(description = "发货时间起(yyyy-MM-dd HH:mm:ss)") @RequestParam(required = false) String shippedFrom,
            @Parameter(description = "发货时间止(yyyy-MM-dd HH:mm:ss)") @RequestParam(required = false) String shippedTo,
            @Parameter(description = "拉取条数") @RequestParam(required = false) Integer limit,
            @Parameter(description = "是否使用平台示例数据") @RequestParam(required = false) Boolean useSampleData) {
        requireOwnedIntegration(id);
        try {
            LogisticsImportJobDto result = platformIntegrationApplicationService.syncLogisticsFromPlatform(id,
                    tenantId, orderNumber, trackingNumber, shippedFrom, shippedTo, limit, useSampleData);
            return ResponseEntity.ok(Result.success(result));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("物流同步失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "连通性测试", description = "按集成的认证协议做一次最小代价只读调用，验证店铺地址与凭据是否可用；"
            + "失败也返回 200 并在 data.success=false 里给出原因（不会回传任何凭据明文）。"
            + "会真实使用凭据外呼平台，按写操作门槛保护")
    @PreAuthorize(HAS_PLATFORM_MANAGE)
    @Auditable(action = AuditActions.PLATFORM_INTEGRATION_TEST, label = "平台连通性测试", targetType = "PlatformIntegration")
    @PostMapping("/{id}/test-connection")
    public ResponseEntity<Result<PlatformConnectionTestResult>> testConnection(
            @Parameter(description = "平台集成ID") @PathVariable UUID id) {
        requireOwnedIntegration(id);
        try {
            return ResponseEntity.ok(Result.success(platformIntegrationApplicationService.testConnection(id)));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("连通性测试失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "发货回写", description = "把本地已发货物流单的运单号回写到平台订单；"
            + "回写成功后在物流单上记录平台发货单号，重复调用会被拒绝")
    @PreAuthorize(HAS_PLATFORM_MANAGE)
    @Auditable(action = AuditActions.PLATFORM_FULFILLMENT_PUSH, label = "推送履约单到平台", targetType = "PlatformIntegration")
    @PostMapping("/{id}/push-fulfillment")
    public ResponseEntity<Result<PlatformFulfillmentResult>> pushFulfillmentToPlatform(
            @Parameter(description = "平台集成ID") @PathVariable UUID id,
            @RequestBody PlatformFulfillmentPushRequest request) {
        requireOwnedIntegration(id);
        try {
            PlatformFulfillmentResult result = platformIntegrationApplicationService.pushFulfillmentToPlatform(
                    id, request.getLogisticsOrderId(), request.isNotifyCustomer());
            return ResponseEntity.ok(Result.success(result));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("发货回写失败: " + e.getMessage()));
        }
    }

    /**
     * 按 ID 的写操作统一归属校验（ISS-122）：{@code platform:manage} 可以发给租户角色，
     * 仅靠 {@code @PreAuthorize} 拦不住横向越权；跨租户与不存在都回 404，
     * 不用状态码差异确认他租户资源存在性（与店铺口径一致）。
     */
    private PlatformIntegration requireOwnedIntegration(UUID id) {
        PlatformIntegration existing = platformIntegrationApplicationService.getPlatformIntegrationById(id);
        if (existing == null) {
            throw new EntityNotFoundException("平台集成", String.valueOf(id));
        }
        TenantAccessGuard.requireOwned("平台集成", existing.getTenantId(), String.valueOf(id));
        return existing;
    }

    private PlatformIntegration toAggregate(PlatformIntegrationSaveRequest request) {
        String requestedTenantId = request.getTenantId();
        // 归属只由会话决定：请求体带历史默认桶字面量或缺省时落登录租户，
        // 否则平台集成会连凭据一起写进谁也查不出来的幻影桶（ISS-121，35 条实测残留）
        request.setTenantId(TenantAccessGuard.resolveWriteTenant(requestedTenantId, TenantContext.LEGACY_TENANT_ID));
        // 客户端显式点名的租户必须真实存在：业务表的 tenant_id 是普通列不是外键，
        // 平台管理员点名一个 tenants 表里没有的 UUID 会 200 落库成无主行（ISS-135 实测）。
        // 校验放在越权判定之后，租户用户的跨租户请求仍按「越权」而不是「不存在」回答。
        TenantAccessGuard.requireExistingTenant(requestedTenantId, tenantRepository);
        return request.toAggregate(null);
    }

    /**
     * 更新场景先取回聚合：出参不再回传明文凭据，入参留空的凭据沿用库里的原值。
     * 归属由既有行决定，请求体的 tenantId 不能把行迁到别的租户。
     */
    private PlatformIntegration toAggregateForUpdate(PlatformIntegrationSaveRequest request, PlatformIntegration existing) {
        // 归属由既有行决定：请求体的 tenantId 不能把这行迁到别的租户
        request.setTenantId(existing.getTenantId());
        return request.toAggregate(existing);
    }

    /**
     * 解析读接口的租户范围：
     * <ul>
     *   <li>平台管理员：入参为空返回 {@code null} 表示跨租户全量；入参非空按该租户过滤</li>
     *   <li>普通租户用户：忽略入参，强制使用登录租户，避免横向越权</li>
     * </ul>
     *
     * @return 租户ID；返回 {@code null} 表示不限租户，返回空串表示无登录租户（查不到数据）
     */
    private String resolveTenantScope(String requestedTenantId) {
        if (isPlatformAdmin()) {
            return TenantContext.normalizeRequested(requestedTenantId);
        }
        return TenantContext.hasTenantId() ? TenantContext.getTenantId() : "";
    }

    /**
     * 详情读接口的租户归属校验：平台管理员放行，租户用户只能读本租户的数据。
     */
    private void assertTenantVisible(PlatformIntegration platform) {
        if (isPlatformAdmin()) {
            return;
        }
        String currentTenantId = TenantContext.hasTenantId() ? TenantContext.getTenantId() : null;
        if (currentTenantId == null || !currentTenantId.equals(platform.getTenantId())) {
            throw new BusinessException("无权访问其他租户的数据", "TENANT_ACCESS_DENIED");
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