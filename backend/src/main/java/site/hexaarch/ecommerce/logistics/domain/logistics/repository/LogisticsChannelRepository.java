package site.hexaarch.ecommerce.logistics.domain.logistics.repository;

import site.hexaarch.ecommerce.logistics.domain.logistics.entity.LogisticsChannel;

import java.util.List;
import java.util.Optional;

/**
 * 物流渠道仓储接口，负责持久化物流渠道实体和提供物流渠道的访问方法。
 *
 * @author kenyon
 */
public interface LogisticsChannelRepository {
    /**
     * 保存物流渠道。
     *
     * @param logisticsChannel 物流渠道实体
     * @return 保存后的物流渠道实体
     */
    LogisticsChannel save(LogisticsChannel logisticsChannel);

    /**
     * 根据ID查找物流渠道。
     *
     * @param id 物流渠道ID
     * @return 物流渠道实体，如果不存在则返回Optional.empty()
     */
    Optional<LogisticsChannel> findById(String id);

    /**
     * 查找所有激活的物流渠道。
     *
     * @return 物流渠道实体列表
     */
    List<LogisticsChannel> findActiveChannels();

    /**
     * 根据国家查找支持的物流渠道。
     *
     * @param country 国家代码
     * @return 物流渠道实体列表
     */
    List<LogisticsChannel> findByCountry(String country);

    /**
     * 查找所有物流渠道。
     *
     * @return 物流渠道实体列表
     */
    List<LogisticsChannel> findAll();

    /**
     * 删除物流渠道。
     *
     * @param id 物流渠道ID
     */
    void delete(String id);
}