package site.hexaarch.ecommerce.logistics.interfaces.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.hexaarch.ecommerce.logistics.application.service.OrderApplicationService;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.OrderMapper;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;
import site.hexaarch.ecommerce.logistics.interfaces.dto.order.CreateOrderDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.order.OrderDto;

import java.util.List;

/**
 * 订单控制器，处理订单相关的HTTP请求。
 *
 * @author kenyon
 */
@RestController
@RequestMapping("/api/orders")
@Tag(name = "订单管理", description = "订单相关的API接口")
public class OrderController {
    private final OrderApplicationService orderApplicationService;
    private final OrderMapper orderMapper;

    public OrderController(OrderApplicationService orderApplicationService, OrderMapper orderMapper) {
        this.orderApplicationService = orderApplicationService;
        this.orderMapper = orderMapper;
    }

    @Operation(summary = "创建订单", description = "创建一个新的订单")
    @PostMapping
    public Result<OrderDto> createOrder(@RequestBody CreateOrderDto createOrderDto) {
        var orderItems = orderMapper.toDomainEntityItemList(createOrderDto.getOrderItems());
        var order = orderApplicationService.createOrder(
                "default-tenant",
                createOrderDto.getCustomerId(),
                orderItems,
                createOrderDto.getShippingAddress(),
                createOrderDto.getPaymentInfo()
        );
        return Result.success(orderMapper.toDto(order));
    }

    @Operation(summary = "根据ID获取订单", description = "根据订单ID获取订单详细信息")
    @GetMapping("/{id}")
    public Result<OrderDto> getOrderById(
            @Parameter(description = "订单ID") @PathVariable String id) {
        var order = orderApplicationService.findOrderById(id);
        return Result.success(orderMapper.toDto(order));
    }

    @Operation(summary = "更新订单状态", description = "更新订单的状态")
    @PutMapping("/{id}/status")
    public Result<OrderDto> updateOrderStatus(
            @Parameter(description = "订单ID") @PathVariable String id,
            @RequestParam OrderStatus status) {
        var order = orderApplicationService.updateOrderStatus(id, status);
        return Result.success(orderMapper.toDto(order));
    }

    @Operation(summary = "获取客户的所有订单", description = "根据客户ID获取所有订单")
    @GetMapping("/customer/{customerId}")
    public Result<List<OrderDto>> getOrdersByCustomer(
            @Parameter(description = "客户ID") @PathVariable String customerId) {
        var orders = orderApplicationService.findOrdersByCustomerId(customerId);
        return Result.success(orderMapper.toDtoList(orders));
    }
}