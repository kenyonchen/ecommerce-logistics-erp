package site.hexaarch.ecommerce.logistics.infrastructure.persistence.repository.config;

import org.springframework.stereotype.Repository;
import site.hexaarch.ecommerce.logistics.domain.config.entity.SystemSetting;
import site.hexaarch.ecommerce.logistics.domain.config.repository.SystemSettingRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.config.SystemSettingJpaEntity;
import site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.repository.config.SystemSettingJpaRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 平台配置仓储实现：以配置键为主键做幂等 upsert。
 *
 * @author kenyon
 */
@Repository
public class SystemSettingRepositoryImpl implements SystemSettingRepository {

    private final SystemSettingJpaRepository systemSettingJpaRepository;

    public SystemSettingRepositoryImpl(SystemSettingJpaRepository systemSettingJpaRepository) {
        this.systemSettingJpaRepository = systemSettingJpaRepository;
    }

    @Override
    public List<SystemSetting> findAll() {
        return systemSettingJpaRepository.findAll().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public SystemSetting save(SystemSetting setting) {
        SystemSettingJpaEntity entity = systemSettingJpaRepository.findById(setting.getKey())
                .orElseGet(() -> {
                    SystemSettingJpaEntity created = new SystemSettingJpaEntity();
                    created.setConfigKey(setting.getKey());
                    return created;
                });
        entity.setConfigValue(setting.getValue());
        entity.setConfigGroup(setting.getGroup());
        entity.setUpdatedBy(setting.getUpdatedBy());
        entity.setUpdatedAt(setting.getUpdatedAt() == null ? LocalDateTime.now() : setting.getUpdatedAt());
        return toDomain(systemSettingJpaRepository.save(entity));
    }

    private SystemSetting toDomain(SystemSettingJpaEntity entity) {
        SystemSetting setting = new SystemSetting();
        setting.setKey(entity.getConfigKey());
        setting.setValue(entity.getConfigValue());
        setting.setGroup(entity.getConfigGroup());
        setting.setUpdatedAt(entity.getUpdatedAt());
        setting.setUpdatedBy(entity.getUpdatedBy());
        return setting;
    }
}
