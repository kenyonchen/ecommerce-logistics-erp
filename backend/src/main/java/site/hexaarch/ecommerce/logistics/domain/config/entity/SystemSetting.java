package site.hexaarch.ecommerce.logistics.domain.config.entity;

import lombok.Data;
import site.hexaarch.ecommerce.logistics.domain.config.PlatformConfigKey;

import java.time.LocalDateTime;

/**
 * 单条平台配置项（键值 + 分组）。
 *
 * @author kenyon
 */
@Data
public class SystemSetting {

    private String key;
    private String value;
    private String group;
    private LocalDateTime updatedAt;
    private String updatedBy;

    public static SystemSetting of(PlatformConfigKey configKey, String value, String updatedBy) {
        SystemSetting setting = new SystemSetting();
        setting.setKey(configKey.getKey());
        setting.setValue(value);
        setting.setGroup(configKey.getGroup());
        setting.setUpdatedBy(updatedBy);
        setting.setUpdatedAt(LocalDateTime.now());
        return setting;
    }
}
