package site.hexaarch.ecommerce.logistics.interfaces.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.hexaarch.ecommerce.logistics.application.service.customer.CustomerApplicationService;
import site.hexaarch.ecommerce.logistics.domain.customer.aggregate.Customer;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;

/**
 * 客户控制器，用于处理客户相关的HTTP请求。
 *
 * @author kenyon
 */
@RestController
@RequestMapping("/api/customers")
@Tag(name = "客户管理", description = "客户相关的API接口")
public class CustomerController {

    private final CustomerApplicationService customerApplicationService;

    @Autowired
    public CustomerController(CustomerApplicationService customerApplicationService) {
        this.customerApplicationService = customerApplicationService;
    }

    @Operation(summary = "创建客户信息", description = "创建客户信息")
    @PostMapping
    public ResponseEntity<Result<Customer>> createCustomer(@Parameter(description = "客户名称") String customerName,
                                                           @Parameter(description = "邮箱地址") String email,
                                                           @Parameter(description = "联系电话") String phone,
                                                           @Parameter(description = "联系地址") String address) {
        try {
            Customer createdCustomer = customerApplicationService.createCustomer(customerName, email, phone, address);
            return ResponseEntity.ok(Result.success(createdCustomer));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("客户创建失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "根据ID获取客户信息", description = "根据客户ID获取客户详细信息")
    @GetMapping("/{id}")
    public ResponseEntity<Result<Customer>> getCustomerById(@Parameter(description = "客户ID") @PathVariable String id) {
        try {
            Customer customer = customerApplicationService.findCustomerById(id);
            if (customer != null) {
                return ResponseEntity.ok(Result.success(customer));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Result.fail("客户不存在"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("客户查询失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "根据ID删除客户信息", description = "根据客户ID删除客户详细信息")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> deleteCustomer(@Parameter(description = "客户ID") @PathVariable String id) {
        try {
            customerApplicationService.deleteCustomer(id);
            return ResponseEntity.ok(Result.success(null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("客户删除失败: " + e.getMessage()));
        }
    }
}
