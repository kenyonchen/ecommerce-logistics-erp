package site.hexaarch.ecommerce.logistics.application.query.config;

import lombok.Data;

/**
 * 平台系统配置的读写视图，按界面分块（常规 / 邮件 / 安全）成三组。
 * <p>
 * 落库是扁平的键值（{@code system_configs}），这里按分组聚合，
 * 使 REST 契约与 admin-web 的表单结构一一对应，避免前端自己拼键名。
 * </p>
 *
 * @author kenyon
 */
@Data
public class SystemConfigView {

    private General general = new General();
    private Email email = new Email();
    private Security security = new Security();

    /**
     * 常规设置
     */
    @Data
    public static class General {
        private String siteName;
        private String siteDescription;
        private String contactEmail;
        private String contactPhone;
    }

    /**
     * 邮件设置
     */
    @Data
    public static class Email {
        private String smtpHost;
        private Integer smtpPort;
        private String senderEmail;
        private Boolean enabled;
    }

    /**
     * 安全策略设置
     */
    @Data
    public static class Security {
        private Integer sessionTimeout;
        private Integer maxLoginAttempts;
        private Boolean twoFactorAuthRequired;
        private Boolean passwordPolicyEnabled;
    }
}
