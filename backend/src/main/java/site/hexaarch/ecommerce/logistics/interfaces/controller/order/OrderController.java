package site.hexaarch.ecommerce.logistics.interfaces.controller.order;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import site.hexaarch.ecommerce.logistics.application.service.order.OrderApplicationService;
import site.hexaarch.ecommerce.logistics.domain.audit.AuditActions;
import site.hexaarch.ecommerce.logistics.domain.audit.Auditable;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.order.aggregate.Order;
import site.hexaarch.ecommerce.logistics.domain.order.valueobject.OrderStatus;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.mapper.order.OrderMapper;
import site.hexaarch.ecommerce.logistics.infrastructure.security.TenantContext;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;
import site.hexaarch.ecommerce.logistics.interfaces.dto.order.CreateOrderDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.order.OrderDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.productimport.TargetFieldDto;

import java.util.ArrayList;
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
    private static final String HAS_ORDER_READ =
            "hasRole('PLATFORM_ADMIN') or hasAuthority('order:read')";
    private static final String HAS_ORDER_WRITE =
            "hasRole('PLATFORM_ADMIN') or hasAuthority('order:write')";

    private final OrderApplicationService orderApplicationService;
    private final OrderMapper orderMapper;

    public OrderController(OrderApplicationService orderApplicationService, OrderMapper orderMapper) {
        this.orderApplicationService = orderApplicationService;
        this.orderMapper = orderMapper;
    }

    @Operation(summary = "创建订单", description = "创建一个新的订单")
    @PreAuthorize(HAS_ORDER_WRITE)
    @Auditable(action = AuditActions.ORDER_CREATE, label = "创建订单", targetType = "Order")
    @PostMapping
    public Result<OrderDto> createOrder(@RequestBody CreateOrderDto createOrderDto) {
        var orderItems = orderMapper.toDomainEntityItemList(createOrderDto.getOrderItems());
        var order = orderApplicationService.createOrder(
                resolveTenant(null),
                createOrderDto.getOrderNumber(),
                createOrderDto.getCustomerId(),
                createOrderDto.getCustomerName(),
                createOrderDto.getCustomerEmail(),
                orderItems,
                createOrderDto.getShippingAddress(),
                createOrderDto.getPaymentInfo()
        );
        return Result.success(toDto(order));
    }

    @Operation(summary = "分页查询订单", description = "按订单号/客户/状态/平台/创建时间等条件分页查询订单")
    @PreAuthorize(HAS_ORDER_READ)
    @GetMapping
    public Result<PageResult<OrderDto>> searchOrders(
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @Parameter(description = "订单号（模糊）") @RequestParam(required = false) String orderNumber,
            @Parameter(description = "客户ID") @RequestParam(required = false) String customerId,
            @Parameter(description = "客户名称（模糊）") @RequestParam(required = false) String customerName,
            @Parameter(description = "订单状态") @RequestParam(required = false) OrderStatus status,
            @Parameter(description = "来源平台") @RequestParam(required = false) String platform,
            @Parameter(description = "关键字（订单号/客户/平台订单号）") @RequestParam(required = false) String keyword,
            @Parameter(description = "创建时间起(yyyy-MM-dd)") @RequestParam(required = false) String startDate,
            @Parameter(description = "创建时间止(yyyy-MM-dd)") @RequestParam(required = false) String endDate,
            @Parameter(description = "页码，从0开始") @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "每页条数") @RequestParam(required = false, defaultValue = "10") Integer size) {
        PageResult<Order> orders = orderApplicationService.searchOrders(resolveTenant(tenantId), orderNumber,
                customerId, customerName, status, platform, keyword, startDate, endDate,
                page == null ? 0 : page, size == null ? 10 : size);
        return Result.success(toDtoPage(orders));
    }

    @Operation(summary = "订单状态字典", description = "返回全部订单状态及其中文名称，前端下拉框使用")
    @PreAuthorize(HAS_ORDER_READ)
    @GetMapping("/statuses")
    public Result<List<TargetFieldDto>> statuses() {
        List<TargetFieldDto> statuses = new ArrayList<>();
        for (OrderStatus status : OrderStatus.values()) {
            statuses.add(new TargetFieldDto(status.name(), status.getLabel(), Boolean.FALSE));
        }
        return Result.success(statuses);
    }

    @Operation(summary = "根据订单号获取订单", description = "根据系统订单号获取订单详情")
    @PreAuthorize(HAS_ORDER_READ)
    @GetMapping("/number/{orderNumber}")
    public Result<OrderDto> getOrderByNumber(
            @Parameter(description = "订单号") @PathVariable String orderNumber) {
        var order = orderApplicationService.findOrderByNumber(orderNumber);
        return Result.success(toDto(order));
    }

    @Operation(summary = "根据ID获取订单", description = "根据订单ID获取订单详细信息")
    @PreAuthorize(HAS_ORDER_READ)
    @GetMapping("/{id}")
    public Result<OrderDto> getOrderById(
            @Parameter(description = "订单ID") @PathVariable String id) {
        var order = orderApplicationService.findOrderById(id);
        return Result.success(toDto(order));
    }

    @Operation(summary = "更新订单状态", description = "更新订单的状态（按状态机校验合法性）")
    @PreAuthorize(HAS_ORDER_WRITE)
    @Auditable(action = AuditActions.ORDER_STATUS_UPDATE, label = "更新订单状态", targetType = "Order")
    @PutMapping("/{id}/status")
    public Result<OrderDto> updateOrderStatus(
            @Parameter(description = "订单ID") @PathVariable String id,
            @Parameter(description = "目标状态") @RequestParam OrderStatus status) {
        var order = orderApplicationService.updateOrderStatus(id, status);
        return Result.success(toDto(order));
    }

    @Operation(summary = "取消订单", description = "取消订单（已发货/已完成不可取消）")
    @PreAuthorize(HAS_ORDER_WRITE)
    @Auditable(action = AuditActions.ORDER_CANCEL, label = "取消订单", targetType = "Order")
    @PostMapping("/{id}/cancel")
    public Result<OrderDto> cancelOrder(
            @Parameter(description = "订单ID") @PathVariable String id) {
        var order = orderApplicationService.cancelOrder(id);
        return Result.success(toDto(order));
    }

    @Operation(summary = "删除订单", description = "根据订单ID删除订单")
    @PreAuthorize(HAS_ORDER_WRITE)
    @Auditable(action = AuditActions.ORDER_DELETE, label = "删除订单（连同联动物流单）", targetType = "Order")
    @DeleteMapping("/{id}")
    public Result<Void> deleteOrder(
            @Parameter(description = "订单ID") @PathVariable String id) {
        orderApplicationService.deleteOrder(id);
        return Result.success();
    }

    @Operation(summary = "获取客户的所有订单", description = "根据客户ID获取所有订单")
    @PreAuthorize(HAS_ORDER_READ)
    @GetMapping("/customer/{customerId}")
    public Result<List<OrderDto>> getOrdersByCustomer(
            @Parameter(description = "客户ID") @PathVariable String customerId) {
        var orders = orderApplicationService.findOrdersByCustomerId(customerId);
        return Result.success(orderMapper.toDtoList(orders));
    }

    private OrderDto toDto(Order order) {
        OrderDto dto = orderMapper.toDto(order);
        if (dto == null) {
            // 单测中 mapper 可能为 mock，生产环境由 MapStruct 实现，不会为空
            return null;
        }
        dto.applyStatusDerivedFields(order);
        return dto;
    }

    private PageResult<OrderDto> toDtoPage(PageResult<Order> orders) {
        List<OrderDto> content = orderMapper.toDtoList(orders.getContent());
        if (content == null) {
            content = new ArrayList<>();
        }
        return new PageResult<>(content, orders.getTotal(), orders.getPage(), orders.getSize());
    }

    /**
     * 租户解析：优先取请求参数，其次取租户上下文，最后回退默认租户
     */
    private String resolveTenant(String tenantId) {
        String requested = TenantContext.normalizeRequested(tenantId);
        if (requested != null) {
            return requested;
        }
        if (TenantContext.hasTenantId()) {
            return TenantContext.getTenantId();
        }
        return OrderApplicationService.DEFAULT_TENANT_ID;
    }
}
