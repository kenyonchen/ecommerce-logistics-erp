package site.hexaarch.ecommerce.logistics.interfaces.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.hexaarch.ecommerce.logistics.application.service.LogisticsApplicationService;
import site.hexaarch.ecommerce.logistics.domain.logistics.aggregate.LogisticsOrder;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsStatus;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;

import java.util.List;

/**
 * 物流控制器，处理物流单相关的HTTP请求。
 *
 * @author kenyon
 */
@RestController
@RequestMapping("/api/logistics")
@Tag(name = "物流管理", description = "物流单相关的API接口")
public class LogisticsController {
    private final LogisticsApplicationService logisticsApplicationService;

    public LogisticsController(LogisticsApplicationService logisticsApplicationService) {
        this.logisticsApplicationService = logisticsApplicationService;
    }

    @Operation(summary = "创建物流单", description = "为指定订单创建物流单")
    @PostMapping
    public Result<LogisticsOrder> createLogisticsOrder(
            @RequestParam String tenantId,
            @RequestParam String orderId,
            @RequestParam String logisticsChannelId) {
        var logisticsOrder = logisticsApplicationService.createLogisticsOrder(tenantId, orderId, logisticsChannelId);
        return Result.success(logisticsOrder);
    }

    @Operation(summary = "根据ID获取物流单", description = "根据物流单ID获取详细信息")
    @GetMapping("/{id}")
    public Result<LogisticsOrder> getLogisticsOrderById(
            @Parameter(description = "物流单ID") @PathVariable String id) {
        var logisticsOrder = logisticsApplicationService.findLogisticsOrderById(id);
        return Result.success(logisticsOrder);
    }

    @Operation(summary = "获取订单的物流单", description = "根据订单ID获取相关的物流单")
    @GetMapping("/order/{orderId}")
    public Result<List<LogisticsOrder>> getLogisticsOrderByOrderId(
            @Parameter(description = "订单ID") @PathVariable String orderId) {
        var logisticsOrder = logisticsApplicationService.findLogisticsOrderByOrderId(orderId);
        return Result.success(logisticsOrder);
    }

    @Operation(summary = "更新物流状态", description = "更新物流单的状态")
    @PutMapping("/{id}/status")
    public Result<LogisticsOrder> updateLogisticsStatus(
            @Parameter(description = "物流单ID") @PathVariable String id,
            @RequestParam LogisticsStatus status) {
        var logisticsOrder = logisticsApplicationService.updateLogisticsStatus(id, status);
        return Result.success(logisticsOrder);
    }

    @Operation(summary = "获取所有物流单", description = "获取所有物流单的列表")
    @GetMapping
    public Result<List<LogisticsOrder>> getAllLogisticsOrders() {
        var logisticsOrders = logisticsApplicationService.findAllLogisticsOrders();
        return Result.success(logisticsOrders);
    }
}
