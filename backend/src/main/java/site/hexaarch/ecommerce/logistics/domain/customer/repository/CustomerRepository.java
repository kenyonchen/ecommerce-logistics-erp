package site.hexaarch.ecommerce.logistics.domain.customer.repository;

import site.hexaarch.ecommerce.logistics.domain.customer.aggregate.Customer;
import site.hexaarch.ecommerce.logistics.domain.customer.valueobject.CustomerStatus;

import java.util.List;
import java.util.Optional;

/**
 * 客户仓储接口，负责客户的持久化和查询操作。
 *
 * @author kenyon
 */
public interface CustomerRepository {
    /**
     * 保存客户。
     *
     * @param customer 客户实例
     */
    void save(Customer customer);

    /**
     * 根据客户ID查找客户。
     *
     * @param customerId 客户ID
     * @return 客户实例，若不存在则返回Optional.empty()
     */
    Optional<Customer> findById(String customerId);

    /**
     * 根据状态查找客户。
     *
     * @param status 客户状态
     * @return 客户列表
     */
    List<Customer> findByStatus(CustomerStatus status);

    /**
     * 根据名称查找客户。
     *
     * @param customerName 客户名称
     * @return 客户列表
     */
    List<Customer> findByName(String customerName);

    /**
     * 根据邮箱查找客户。
     *
     * @param email 客户邮箱
     * @return 客户实例，若不存在则返回Optional.empty()
     */
    Optional<Customer> findByEmail(String email);
}
