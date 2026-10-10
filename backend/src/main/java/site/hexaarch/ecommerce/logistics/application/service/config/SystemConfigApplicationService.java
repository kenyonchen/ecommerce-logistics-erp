package site.hexaarch.ecommerce.logistics.application.service.config;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.hexaarch.ecommerce.logistics.application.query.config.SystemConfigView;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.config.PlatformConfigKey;
import site.hexaarch.ecommerce.logistics.domain.config.entity.SystemSetting;
import site.hexaarch.ecommerce.logistics.domain.config.repository.SystemSettingRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 平台系统配置应用服务：读取（含默认值回填）、校验并落库、按类型对外暴露生效值。
 * <p>
 * 保存走单一事务：12 项配置是一个整体，不允许出现"邮箱组存上了、安全组半存"的状态
 * （对应 docs/tech/security-design.md P1-a 事务边界口径）。
 * </p>
 * <p>
 * 消费方：{@code security.sessionTimeout} 注入 JWT 签发有效期，
 * {@code security.passwordPolicyEnabled} 控制口令强度校验，
 * {@code security.maxLoginAttempts} 驱动登录失败锁定；{@code general.*} 与 {@code email.*}
 * 目前只持久化与回显（邮件发送链路后端未实现，见 docs/qa/known-issues.md 的说明），
 * 因此本服务只保证"存得下、读得出、校验得住"，不假装邮件已发出。
 * </p>
 *
 * @author kenyon
 */
@Service
public class SystemConfigApplicationService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public static final int SESSION_TIMEOUT_MIN_SECONDS = 300;
    public static final int SESSION_TIMEOUT_MAX_SECONDS = 604_800;
    public static final int MAX_LOGIN_ATTEMPTS_UPPER_BOUND = 100;

    private final SystemSettingRepository systemSettingRepository;

    public SystemConfigApplicationService(SystemSettingRepository systemSettingRepository) {
        this.systemSettingRepository = systemSettingRepository;
    }

    /**
     * 当前配置（库中没有的键按目录默认值回填）
     */
    @Transactional(readOnly = true)
    public SystemConfigView current() {
        return toView(loadValues());
    }

    /**
     * 校验并保存全部配置，返回保存后的配置
     */
    @Transactional
    public SystemConfigView update(SystemConfigView requested, String updatedBy) {
        Map<String, String> validated = normalizeAndValidate(requested);
        validated.forEach((key, value) ->
                systemSettingRepository.save(SystemSetting.of(PlatformConfigKey.fromKey(key), value, updatedBy)));
        return toView(loadValues());
    }

    /**
     * 会话超时（秒）：访问令牌有效期用
     */
    @Transactional(readOnly = true)
    public int sessionTimeoutSeconds() {
        return intValue(PlatformConfigKey.SESSION_TIMEOUT);
    }

    /**
     * 登录失败锁定阈值；{@code <= 0} 表示关闭锁定
     */
    @Transactional(readOnly = true)
    public int maxLoginAttempts() {
        return intValue(PlatformConfigKey.MAX_LOGIN_ATTEMPTS);
    }

    @Transactional(readOnly = true)
    public boolean isPasswordPolicyEnabled() {
        return booleanValue(PlatformConfigKey.PASSWORD_POLICY_ENABLED);
    }

    /**
     * 口令强度：启用密码策略时要求至少 8 位
     */
    public void requireAcceptablePassword(String password) {
        if (!isPasswordPolicyEnabled()) {
            return;
        }
        if (password == null || password.isBlank()) {
            throw new ValidationException("口令不能为空");
        }
        if (password.length() < 8) {
            throw new ValidationException("已启用密码策略：口令至少需要 8 位");
        }
    }

    private Map<String, String> loadValues() {
        Map<String, String> values = new LinkedHashMap<>();
        for (SystemSetting setting : systemSettingRepository.findAll()) {
            if (setting.getKey() != null && setting.getValue() != null) {
                values.putIfAbsent(setting.getKey(), setting.getValue());
            }
        }
        return values;
    }

    private SystemConfigView toView(Map<String, String> values) {
        SystemConfigView view = new SystemConfigView();
        SystemConfigView.General general = view.getGeneral();
        general.setSiteName(stringValue(values, PlatformConfigKey.SITE_NAME));
        general.setSiteDescription(stringValue(values, PlatformConfigKey.SITE_DESCRIPTION));
        general.setContactEmail(stringValue(values, PlatformConfigKey.CONTACT_EMAIL));
        general.setContactPhone(stringValue(values, PlatformConfigKey.CONTACT_PHONE));

        SystemConfigView.Email email = view.getEmail();
        email.setSmtpHost(stringValue(values, PlatformConfigKey.SMTP_HOST));
        email.setSmtpPort(parseInt(stringValue(values, PlatformConfigKey.SMTP_PORT),
                Integer.parseInt(PlatformConfigKey.SMTP_PORT.getDefaultValue())));
        email.setSenderEmail(stringValue(values, PlatformConfigKey.SENDER_EMAIL));
        email.setEnabled(parseBoolean(stringValue(values, PlatformConfigKey.EMAIL_ENABLED),
                Boolean.parseBoolean(PlatformConfigKey.EMAIL_ENABLED.getDefaultValue())));

        SystemConfigView.Security security = view.getSecurity();
        security.setSessionTimeout(parseInt(stringValue(values, PlatformConfigKey.SESSION_TIMEOUT),
                Integer.parseInt(PlatformConfigKey.SESSION_TIMEOUT.getDefaultValue())));
        security.setMaxLoginAttempts(parseInt(stringValue(values, PlatformConfigKey.MAX_LOGIN_ATTEMPTS),
                Integer.parseInt(PlatformConfigKey.MAX_LOGIN_ATTEMPTS.getDefaultValue())));
        security.setTwoFactorAuthRequired(parseBoolean(
                stringValue(values, PlatformConfigKey.TWO_FACTOR_REQUIRED),
                Boolean.parseBoolean(PlatformConfigKey.TWO_FACTOR_REQUIRED.getDefaultValue())));
        security.setPasswordPolicyEnabled(parseBoolean(
                stringValue(values, PlatformConfigKey.PASSWORD_POLICY_ENABLED),
                Boolean.parseBoolean(PlatformConfigKey.PASSWORD_POLICY_ENABLED.getDefaultValue())));
        return view;
    }

    private Map<String, String> normalizeAndValidate(SystemConfigView requested) {
        if (requested == null) {
            throw new ValidationException("系统配置不能为空");
        }
        SystemConfigView.General general = requested.getGeneral() == null
                ? new SystemConfigView.General() : requested.getGeneral();
        SystemConfigView.Email email = requested.getEmail() == null
                ? new SystemConfigView.Email() : requested.getEmail();
        SystemConfigView.Security security = requested.getSecurity() == null
                ? new SystemConfigView.Security() : requested.getSecurity();

        Map<String, String> values = new LinkedHashMap<>();
        values.put(PlatformConfigKey.SITE_NAME.getKey(), requireText("站点名称", general.getSiteName(), 100));
        values.put(PlatformConfigKey.SITE_DESCRIPTION.getKey(), optionalText("站点描述", general.getSiteDescription(), 500));
        values.put(PlatformConfigKey.CONTACT_EMAIL.getKey(), optionalEmail("联系邮箱", general.getContactEmail()));
        values.put(PlatformConfigKey.CONTACT_PHONE.getKey(), optionalText("联系电话", general.getContactPhone(), 32));

        values.put(PlatformConfigKey.SMTP_HOST.getKey(), optionalText("SMTP主机", email.getSmtpHost(), 128));
        values.put(PlatformConfigKey.SMTP_PORT.getKey(),
                String.valueOf(requiredInt("SMTP端口", email.getSmtpPort(), 1, 65535)));
        values.put(PlatformConfigKey.SENDER_EMAIL.getKey(), optionalEmail("发件人邮箱", email.getSenderEmail()));
        values.put(PlatformConfigKey.EMAIL_ENABLED.getKey(),
                String.valueOf(Boolean.TRUE.equals(email.getEnabled())));

        values.put(PlatformConfigKey.SESSION_TIMEOUT.getKey(), String.valueOf(requiredInt("会话超时(秒)",
                security.getSessionTimeout(), SESSION_TIMEOUT_MIN_SECONDS, SESSION_TIMEOUT_MAX_SECONDS)));
        values.put(PlatformConfigKey.MAX_LOGIN_ATTEMPTS.getKey(), String.valueOf(requiredInt("最大登录失败次数",
                security.getMaxLoginAttempts(), 0, MAX_LOGIN_ATTEMPTS_UPPER_BOUND)));
        // 后端没有 TOTP 校验链路，开启这项等于给用户一个"以为有二次验证"的假安全感，因此明确拒绝
        if (Boolean.TRUE.equals(security.getTwoFactorAuthRequired())) {
            throw new ValidationException("双因素认证（TOTP）后端尚未实现，不能开启；请先完成验证码链路再启用该项");
        }
        values.put(PlatformConfigKey.TWO_FACTOR_REQUIRED.getKey(), "false");
        values.put(PlatformConfigKey.PASSWORD_POLICY_ENABLED.getKey(),
                String.valueOf(Boolean.TRUE.equals(security.getPasswordPolicyEnabled())));
        return values;
    }

    private String requireText(String label, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(label + "不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new ValidationException(label + "长度不能超过 " + maxLength + " 个字符");
        }
        return trimmed;
    }

    private String optionalText(String label, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return requireText(label, value, maxLength);
    }

    private String optionalEmail(String label, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > 128 || !EMAIL_PATTERN.matcher(trimmed).matches()) {
            throw new ValidationException(label + "不是合法的邮箱地址");
        }
        return trimmed;
    }

    private int requiredInt(String label, Integer value, int min, int max) {
        if (value == null) {
            throw new ValidationException(label + "不能为空");
        }
        if (value < min || value > max) {
            throw new ValidationException(label + "取值需在 " + min + " 到 " + max + " 之间");
        }
        return value;
    }

    private String stringValue(Map<String, String> values, PlatformConfigKey key) {
        return values.getOrDefault(key.getKey(), key.getDefaultValue());
    }

    private int intValue(PlatformConfigKey key) {
        return parseInt(stringValue(loadValues(), key), Integer.parseInt(key.getDefaultValue()));
    }

    private boolean booleanValue(PlatformConfigKey key) {
        return parseBoolean(stringValue(loadValues(), key), Boolean.parseBoolean(key.getDefaultValue()));
    }

    private int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private boolean parseBoolean(String value, boolean fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String trimmed = value.trim();
        if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            return Boolean.parseBoolean(trimmed);
        }
        return fallback;
    }
}
