package site.hexaarch.ecommerce.logistics.domain.config.repository;

import site.hexaarch.ecommerce.logistics.domain.config.entity.SystemSetting;

import java.util.List;

/**
 * 平台配置仓储。
 *
 * @author kenyon
 */
public interface SystemSettingRepository {

    List<SystemSetting> findAll();

    /**
     * 按 key 幂等写入（存在则更新值与更新时间）
     */
    SystemSetting save(SystemSetting setting);
}
