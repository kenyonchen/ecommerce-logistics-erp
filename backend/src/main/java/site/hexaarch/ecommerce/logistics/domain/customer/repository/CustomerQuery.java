package site.hexaarch.ecommerce.logistics.domain.customer.repository;

import site.hexaarch.ecommerce.logistics.domain.customer.valueobject.CustomerStatus;

import java.time.LocalDateTime;

/**
 * 客户列表查询条件。
 *
 * @param tenantId       租户ID（为空表示不按租户过滤）
 * @param customerId     客户ID（精确）
 * @param customerName   客户名称（模糊）
 * @param email          邮箱（模糊）
 * @param phone          电话（模糊）
 * @param status         客户状态
 * @param sourcePlatform 来源平台
 * @param keyword        关键字，同时匹配客户ID/名称/邮箱/电话
 * @param createdFrom    注册时间起（含）
 * @param createdTo      注册时间止（含）
 * @param page           页码，从0开始
 * @param size           每页条数
 * @param sort           排序，格式：字段,asc|desc
 * @author kenyon
 */
public record CustomerQuery(String tenantId,
                            String customerId,
                            String customerName,
                            String email,
                            String phone,
                            CustomerStatus status,
                            String sourcePlatform,
                            String keyword,
                            LocalDateTime createdFrom,
                            LocalDateTime createdTo,
                            int page,
                            int size,
                            String sort) {

    public static CustomerQuery of(String tenantId) {
        return new CustomerQuery(tenantId, null, null, null, null, null, null, null, null, null, 0, 10, null);
    }
}
