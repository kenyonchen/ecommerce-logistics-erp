package site.hexaarch.ecommerce.logistics.interfaces.controller.customer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
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
import site.hexaarch.ecommerce.logistics.application.service.customer.CustomerApplicationService;
import site.hexaarch.ecommerce.logistics.domain.audit.Auditable;
import site.hexaarch.ecommerce.logistics.domain.audit.AuditActions;
import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.customer.aggregate.Customer;
import site.hexaarch.ecommerce.logistics.domain.customer.repository.CustomerQuery;
import site.hexaarch.ecommerce.logistics.domain.customer.valueobject.CustomerStatus;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;
import site.hexaarch.ecommerce.logistics.interfaces.dto.customer.CustomerCreateRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.customer.CustomerDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.customer.CustomerUpdateRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 客户控制器，用于处理客户相关的HTTP请求。
 * <p>
 * 接口约定：所有响应统一为 {@code Result<T>}，成功码为字符串 {@code "200"}；
 * 异常统一交由 {@code GlobalExceptionHandler} 处理（不存在→404，校验失败→400）。
 * </p>
 *
 * @author kenyon
 */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Tag(name = "客户管理", description = "客户（平台买家）相关的API接口")
public class CustomerController {
    private static final String HAS_CUSTOMER_READ =
            "hasRole('PLATFORM_ADMIN') or hasAuthority('customer:read')";
    private static final String HAS_CUSTOMER_WRITE =
            "hasRole('PLATFORM_ADMIN') or hasAuthority('customer:write')";


    private final CustomerApplicationService customerApplicationService;

    @Operation(summary = "分页查询客户", description = "支持客户ID、名称、邮箱、电话、状态、注册时间等条件的分页查询")
    @PreAuthorize(HAS_CUSTOMER_READ)
    @GetMapping
    public Result<PageResult<CustomerDto>> listCustomers(
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @Parameter(description = "客户ID（精确）") @RequestParam(required = false) String customerId,
            @Parameter(description = "客户名称（模糊）") @RequestParam(required = false) String customerName,
            @Parameter(description = "邮箱（模糊）") @RequestParam(required = false) String email,
            @Parameter(description = "电话（模糊）") @RequestParam(required = false) String phone,
            @Parameter(description = "状态：ACTIVE/INACTIVE/SUSPENDED/DELETED") @RequestParam(required = false) String status,
            @Parameter(description = "来源平台") @RequestParam(required = false) String sourcePlatform,
            @Parameter(description = "关键字（匹配客户ID/名称/邮箱/电话）") @RequestParam(required = false) String keyword,
            @Parameter(description = "注册时间起，格式 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss") @RequestParam(required = false) String createdFrom,
            @Parameter(description = "注册时间止，格式同上") @RequestParam(required = false) String createdTo,
            @Parameter(description = "页码，从0开始") @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "每页条数") @RequestParam(required = false, defaultValue = "10") Integer size,
            @Parameter(description = "排序，格式：字段,asc|desc") @RequestParam(required = false, defaultValue = "createdAt,desc") String sort) {
        CustomerStatus customerStatus = parseStatus(status);
        // 租户范围交给应用服务的 TenantAccessGuard 收敛：控制器再把缺省值硬写成 default-tenant，
        // 会让非默认租户的用户带上错误租户被守卫判为越权（GET /api/customers 直接 403）。
        CustomerQuery query = new CustomerQuery(
                tenantId,
                customerId, customerName, email, phone, customerStatus, sourcePlatform, keyword,
                parseDate(createdFrom, false), parseDate(createdTo, true),
                page == null ? 0 : page, size == null ? 10 : size, sort);
        PageResult<Customer> result = customerApplicationService.searchCustomers(query);
        return Result.success(result.map(CustomerController::toDto));
    }

    @Operation(summary = "客户状态下拉字典", description = "供前端渲染状态筛选下拉框")
    @PreAuthorize(HAS_CUSTOMER_READ)
    @GetMapping("/status-options")
    public Result<List<CustomerDto.StatusOption>> statusOptions() {
        return Result.success(List.of(
                new CustomerDto.StatusOption(CustomerStatus.ACTIVE.name(), CustomerStatus.ACTIVE.getDescription()),
                new CustomerDto.StatusOption(CustomerStatus.INACTIVE.name(), CustomerStatus.INACTIVE.getDescription()),
                new CustomerDto.StatusOption(CustomerStatus.SUSPENDED.name(), CustomerStatus.SUSPENDED.getDescription()),
                new CustomerDto.StatusOption(CustomerStatus.DELETED.name(), CustomerStatus.DELETED.getDescription())
        ));
    }

    @Operation(summary = "根据ID获取客户信息", description = "根据客户ID获取客户详细信息")
    @PreAuthorize(HAS_CUSTOMER_READ)
    @GetMapping("/{id}")
    public Result<CustomerDto> getCustomerById(@Parameter(description = "客户ID") @PathVariable String id) {
        return Result.success(toDto(customerApplicationService.findCustomerById(id)));
    }

    @Operation(summary = "创建客户信息", description = "创建客户信息，邮箱非空时全局唯一")
    @PreAuthorize(HAS_CUSTOMER_WRITE)
    @Auditable(action = AuditActions.CUSTOMER_CREATE, label = "创建客户", targetType = "Customer")
    @PostMapping
    public Result<CustomerDto> createCustomer(@RequestBody CustomerCreateRequest request) {
        Customer createdCustomer = customerApplicationService.createCustomer(
                request.getTenantId(), request.getCustomerName(), request.getEmail(),
                request.getPhone(), request.getAddress(), request.getCompany());
        return Result.success(toDto(createdCustomer));
    }

    @Operation(summary = "更新客户资料", description = "更新客户名称/邮箱/电话/地址，传空字段保持原值")
    @PreAuthorize(HAS_CUSTOMER_WRITE)
    @Auditable(action = AuditActions.CUSTOMER_UPDATE, label = "更新客户资料", targetType = "Customer")
    @PutMapping("/{id}")
    public Result<CustomerDto> updateCustomer(@Parameter(description = "客户ID") @PathVariable String id,
                                              @RequestBody CustomerUpdateRequest request) {
        Customer customer = customerApplicationService.updateCustomer(id, request.getCustomerName(),
                request.getEmail(), request.getPhone(), request.getAddress(), request.getCompany());
        return Result.success(toDto(customer));
    }

    @Operation(summary = "激活客户", description = "将客户状态置为 ACTIVE")
    @PreAuthorize(HAS_CUSTOMER_WRITE)
    @Auditable(action = AuditActions.CUSTOMER_STATUS_UPDATE, label = "激活客户", targetType = "Customer")
    @PutMapping("/{id}/activate")
    public Result<CustomerDto> activateCustomer(@Parameter(description = "客户ID") @PathVariable String id) {
        return Result.success(toDto(customerApplicationService.activateCustomer(id)));
    }

    @Operation(summary = "停用客户", description = "将客户状态置为 INACTIVE")
    @PreAuthorize(HAS_CUSTOMER_WRITE)
    @Auditable(action = AuditActions.CUSTOMER_STATUS_UPDATE, label = "停用客户", targetType = "Customer")
    @PutMapping("/{id}/deactivate")
    public Result<CustomerDto> deactivateCustomer(@Parameter(description = "客户ID") @PathVariable String id) {
        return Result.success(toDto(customerApplicationService.deactivateCustomer(id)));
    }

    @Operation(summary = "暂停客户", description = "将客户状态置为 SUSPENDED")
    @PreAuthorize(HAS_CUSTOMER_WRITE)
    @Auditable(action = AuditActions.CUSTOMER_STATUS_UPDATE, label = "暂停客户", targetType = "Customer")
    @PutMapping("/{id}/suspend")
    public Result<CustomerDto> suspendCustomer(@Parameter(description = "客户ID") @PathVariable String id) {
        return Result.success(toDto(customerApplicationService.suspendCustomer(id)));
    }

    @Operation(summary = "根据ID删除客户信息", description = "软删除：状态置为 DELETED，历史订单仍可查询")
    @PreAuthorize(HAS_CUSTOMER_WRITE)
    @Auditable(action = AuditActions.CUSTOMER_DELETE, label = "删除客户（软删除）", targetType = "Customer")
    @DeleteMapping("/{id}")
    public Result<CustomerDto> deleteCustomer(@Parameter(description = "客户ID") @PathVariable String id) {
        return Result.success(toDto(customerApplicationService.deleteCustomer(id)));
    }

    static CustomerDto toDto(Customer customer) {
        CustomerDto dto = new CustomerDto();
        dto.setCustomerId(customer.getCustomerId());
        dto.setTenantId(customer.getTenantId());
        dto.setCustomerName(customer.getCustomerName());
        dto.setEmail(customer.getEmail());
        dto.setPhone(customer.getPhone());
        dto.setAddress(customer.getAddress());
        dto.setCompany(customer.getCompany());
        dto.setStatus(customer.getStatus().name());
        dto.setStatusLabel(customer.getStatus().getDescription());
        dto.setSourcePlatform(customer.getSourcePlatform());
        dto.setExternalBuyerId(customer.getExternalBuyerId());
        dto.setCreatedAt(customer.getCreatedAt());
        dto.setUpdatedAt(customer.getUpdatedAt());
        return dto;
    }

    private static CustomerStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return CustomerStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("不支持的客户状态: " + status);
        }
    }

    private static LocalDateTime parseDate(String text, boolean endOfDay) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String value = text.trim();
        try {
            return LocalDateTime.parse(value.replace(' ', 'T'));
        } catch (DateTimeParseException ignored) {
            // 继续尝试按日期解析
        }
        try {
            LocalDate date = LocalDate.parse(value);
            return endOfDay ? date.atTime(23, 59, 59) : date.atStartOfDay();
        } catch (DateTimeParseException e) {
            throw new ValidationException("时间格式不正确（应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss）: " + text);
        }
    }
}
