package site.hexaarch.ecommerce.logistics.domain.config;

import java.util.List;

/**
 * 平台系统配置项目录：键名、分组、类型与默认值的唯一定义处。
 * <p>
 * 配置采用「键值 + 分组」落库而非固定列，是因为界面按分组成块读写、
 * 且新增一项不该改动表结构（本项目走 Hibernate {@code ddl-auto: update}，见 ADR-0002）。
 * </p>
 * <p>
 * ⚠️ 新增键时必须同步 {@code SystemConfigApplicationService} 里的校验规则，
 * 未登记的键在保存时会被拒绝，避免脏键无限增长。
 * </p>
 *
 * @author kenyon
 */
public enum PlatformConfigKey {

    SITE_NAME("general.siteName", "站点名称", ConfigType.STRING, "跨境物流ERP"),
    SITE_DESCRIPTION("general.siteDescription", "站点描述", ConfigType.STRING, "综合电商物流ERP管理系统"),
    CONTACT_EMAIL("general.contactEmail", "联系邮箱", ConfigType.STRING, "admin@example.com"),
    CONTACT_PHONE("general.contactPhone", "联系电话", ConfigType.STRING, "400-123-4567"),

    SMTP_HOST("email.smtpHost", "SMTP主机", ConfigType.STRING, ""),
    SMTP_PORT("email.smtpPort", "SMTP端口", ConfigType.INTEGER, "587"),
    SENDER_EMAIL("email.senderEmail", "发件人邮箱", ConfigType.STRING, ""),
    EMAIL_ENABLED("email.enabled", "启用邮件服务", ConfigType.BOOLEAN, "false"),

    SESSION_TIMEOUT("security.sessionTimeout", "会话超时（秒）", ConfigType.INTEGER, "86400"),
    MAX_LOGIN_ATTEMPTS("security.maxLoginAttempts", "最大登录失败次数", ConfigType.INTEGER, "5"),
    TWO_FACTOR_REQUIRED("security.twoFactorAuthRequired", "强制双因素认证", ConfigType.BOOLEAN, "false"),
    PASSWORD_POLICY_ENABLED("security.passwordPolicyEnabled", "启用密码策略", ConfigType.BOOLEAN, "true");

    private final String key;
    private final String label;
    private final ConfigType type;
    private final String defaultValue;

    PlatformConfigKey(String key, String label, ConfigType type, String defaultValue) {
        this.key = key;
        this.label = label;
        this.type = type;
        this.defaultValue = defaultValue;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public ConfigType getType() {
        return type;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * 键名形如 {@code general.siteName}，前段即分组
     */
    public String getGroup() {
        return key.substring(0, key.indexOf('.'));
    }

    public static PlatformConfigKey fromKey(String key) {
        for (PlatformConfigKey value : values()) {
            if (value.key.equals(key)) {
                return value;
            }
        }
        return null;
    }

    public static List<PlatformConfigKey> all() {
        return List.of(values());
    }

    /**
     * 配置值类型
     */
    public enum ConfigType {
        STRING,
        INTEGER,
        BOOLEAN
    }
}
