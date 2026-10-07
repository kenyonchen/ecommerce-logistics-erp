package site.hexaarch.ecommerce.logistics.interfaces.controller.logistics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.hexaarch.ecommerce.logistics.application.service.logistics.LogisticsApplicationService;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsStatus;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.AddTraceRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.BatchShipRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.BatchShipResultDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.CreateLogisticsRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.LogisticsOrderDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.TargetFieldDto;

import java.util.ArrayList;
import java.util.List;

/**
 * 物流控制器，处理物流单相关的HTTP请求。
 * <p>
 * 接口分组：
 * <ul>
 *     <li>查询：{@code GET /api/logistics}（条件分页）、{@code GET /api/logistics/{id}}、{@code GET /api/logistics/order/{orderId}}</li>
 *     <li>维护：{@code POST /api/logistics}、{@code PUT /api/logistics/{id}/status}、{@code DELETE /api/logistics/{id}}</li>
 *     <li>发货与面单：{@code POST /api/logistics/{id}/ship}、{@code /label}、{@code /cancel}、{@code /track}、{@code /traces}</li>
 *     <li>批量发货：{@code POST /api/logistics/batch-ship}</li>
 * </ul>
 * 物流数据均以内网 DTO（{@link LogisticsOrderDto}）输出，不直接序列化领域聚合。
 * </p>
 *
 * @author kenyon
 */
@Slf4j
@RestController
@RequestMapping("/api/logistics")
@RequiredArgsConstructor
@Tag(name = "物流管理", description = "物流单查询、状态推进、发货与批量发货的API接口")
public class LogisticsController {

    private final LogisticsApplicationService logisticsApplicationService;

    /**
     * 物流状态更新请求体
     */
    @Data
    public static class UpdateLogisticsStatusRequest {
        private LogisticsStatus status;
        private String description;
    }

    @Operation(summary = "创建物流单", description = "为指定订单创建物流单，订单可按订单ID或订单号指定，渠道可按渠道ID或渠道编码指定")
    @PostMapping
    public Result<LogisticsOrderDto> createLogisticsOrder(@RequestBody CreateLogisticsRequest request) {
        return Result.success(logisticsApplicationService.createLogisticsOrder(request));
    }

    @Operation(summary = "条件分页查询物流单", description = "按运单号、订单、渠道、状态、关键字与时间区间分页查询物流单")
    @GetMapping
    public Result<PageResult<LogisticsOrderDto>> searchLogisticsOrders(
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @Parameter(description = "运单号") @RequestParam(required = false) String trackingNumber,
            @Parameter(description = "订单ID") @RequestParam(required = false) String orderId,
            @Parameter(description = "渠道ID或渠道编码") @RequestParam(required = false) String channelId,
            @Parameter(description = "物流状态") @RequestParam(required = false) String status,
            @Parameter(description = "关键字（物流单号/运单号/承运商）") @RequestParam(required = false) String keyword,
            @Parameter(description = "创建起始日期 yyyy-MM-dd") @RequestParam(required = false) String startDate,
            @Parameter(description = "创建截止日期 yyyy-MM-dd") @RequestParam(required = false) String endDate,
            @Parameter(description = "页码，从0开始") @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "每页条数") @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.success(logisticsApplicationService.search(tenantId, trackingNumber, orderId, channelId,
                status, keyword, startDate, endDate, page == null ? 0 : page, size == null ? 20 : size));
    }

    @Operation(summary = "物流状态字典", description = "返回全部物流状态及其中文名称，前端下拉框与状态标签使用")
    @GetMapping("/statuses")
    public Result<List<TargetFieldDto>> statuses() {
        List<TargetFieldDto> statuses = new ArrayList<>();
        for (LogisticsStatus status : LogisticsStatus.values()) {
            statuses.add(new TargetFieldDto(status.name(), status.getDescription(), Boolean.FALSE));
        }
        return Result.success(statuses);
    }

    @Operation(summary = "根据ID获取物流单", description = "根据物流单ID获取详细信息（含轨迹与面单）")
    @GetMapping("/{id}")
    public Result<LogisticsOrderDto> getLogisticsOrderById(
            @Parameter(description = "物流单ID") @PathVariable String id) {
        return Result.success(logisticsApplicationService.getLogisticsOrderDto(id));
    }

    @Operation(summary = "获取订单的物流单", description = "根据订单ID获取相关的物流单")
    @GetMapping("/order/{orderId}")
    public Result<List<LogisticsOrderDto>> getLogisticsOrderByOrderId(
            @Parameter(description = "订单ID") @PathVariable String orderId) {
        return Result.success(logisticsApplicationService.findLogisticsOrderByOrderId(orderId));
    }

    @Operation(summary = "更新物流状态", description = "更新物流单状态，可附带一次轨迹描述")
    @PutMapping("/{id}/status")
    public Result<LogisticsOrderDto> updateLogisticsStatus(
            @Parameter(description = "物流单ID") @PathVariable String id,
            @RequestBody UpdateLogisticsStatusRequest request) {
        return Result.success(logisticsApplicationService.updateLogisticsStatus(id, request.getStatus(),
                request.getDescription()));
    }

    @Operation(summary = "标记发货", description = "为物流单指定运单号并标记为已揽收")
    @PostMapping("/{id}/ship")
    public Result<LogisticsOrderDto> ship(
            @Parameter(description = "物流单ID") @PathVariable String id,
            @Parameter(description = "发货时间 yyyy-MM-dd HH:mm:ss") @RequestParam(required = false) String shippedAt,
            @Parameter(description = "运单号") @RequestParam(required = false) String trackingNumber) {
        return Result.success(logisticsApplicationService.ship(id, shippedAt, trackingNumber));
    }

    @Operation(summary = "取消物流单", description = "取消尚未揽收的物流单")
    @PostMapping("/{id}/cancel")
    public Result<LogisticsOrderDto> cancel(@Parameter(description = "物流单ID") @PathVariable String id) {
        return Result.success(logisticsApplicationService.cancel(id));
    }

    @Operation(summary = "生成物流面单", description = "为物流单生成面单信息")
    @PostMapping("/{id}/label")
    public Result<LogisticsOrderDto> generateLabel(@Parameter(description = "物流单ID") @PathVariable String id) {
        return Result.success(logisticsApplicationService.generateLabel(id));
    }

    @Operation(summary = "刷新物流跟踪", description = "登记一次跟踪查询轨迹并回传当前状态")
    @PostMapping("/{id}/track")
    public Result<LogisticsOrderDto> track(@Parameter(description = "物流单ID") @PathVariable String id) {
        return Result.success(logisticsApplicationService.track(id));
    }

    @Operation(summary = "登记物流轨迹", description = "为物流单追加一条轨迹节点")
    @PostMapping("/{id}/traces")
    public Result<LogisticsOrderDto> addTrace(
            @Parameter(description = "物流单ID") @PathVariable String id,
            @RequestBody AddTraceRequest request) {
        return Result.success(logisticsApplicationService.addTrace(id, request));
    }

    @Operation(summary = "批量发货", description = "按订单批量生成物流单并推进订单状态，单条失败不影响其他订单")
    @PostMapping("/batch-ship")
    public Result<BatchShipResultDto> batchShip(@RequestBody BatchShipRequest request) {
        try {
            return Result.success(logisticsApplicationService.batchShip(request));
        } catch (BusinessException e) {
            log.warn("[BatchShip] 批量发货失败: {}", e.getMessage());
            return Result.error(e.getErrorCode(), e.getMessage());
        }
    }

    @Operation(summary = "删除物流单", description = "删除指定物流单")
    @DeleteMapping("/{id}")
    public Result<Void> deleteLogisticsOrder(@Parameter(description = "物流单ID") @PathVariable String id) {
        logisticsApplicationService.delete(id);
        return Result.success(null);
    }
}
