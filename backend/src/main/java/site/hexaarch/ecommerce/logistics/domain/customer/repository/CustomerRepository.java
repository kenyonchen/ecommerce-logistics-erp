package site.hexaarch.ecommerce.logistics.domain.customer.repository;

import site.hexaarch.ecommerce.logistics.domain.common.PageResult;
import site.hexaarch.ecommerce.logistics.domain.customer.aggregate.Customer;
import site.hexaarch.ecommerce.logistics.domain.customer.valueobject.CustomerStatus;

import java.util.Optional;

/**
 * 客户仓储接口，负责客户的持久化和查询操作。
 *
 * @author kenyon
 */
public interface CustomerRepository {
    /**
     * 保存客户（新建或整体更新）。
     *
     * @param customer 客户实例
     * @return 保存后的客户
     */
    Customer save(Customer customer);

    /**
     * 根据客户ID查找客户。
     *
     * @param customerId 客户ID
     * @return 客户实例，若不存在则返回Optional.empty()
     */
    Optional<Customer> findById(String customerId);

    /**
     * 根据邮箱查找客户（邮箱全局唯一）。
     *
     * @param email 客户邮箱
     * @return 客户实例，若不存在则返回Optional.empty()
     */
    Optional<Customer> findByEmail(String email);

    /**
     * 根据来源平台买家ID查找客户（平台导入幂等键）。
     *
     * @param tenantId        租户ID
     * @param sourcePlatform  来源平台
     * @param externalBuyerId 平台买家ID
     * @return 客户实例，若不存在则返回Optional.empty()
     */
    Optional<Customer> findByExternalBuyerId(String tenantId, String sourcePlatform, String externalBuyerId);

    /**
     * 按状态统计客户数量。
     *
     * @param status 客户状态
     * @return 数量
     */
    long countByStatus(CustomerStatus status);

    /**
     * 条件分页查询客户。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<Customer> findAll(CustomerQuery query);
}
