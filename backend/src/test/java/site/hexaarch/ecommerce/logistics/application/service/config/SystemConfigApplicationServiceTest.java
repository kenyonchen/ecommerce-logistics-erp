package site.hexaarch.ecommerce.logistics.application.service.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.hexaarch.ecommerce.logistics.application.query.config.SystemConfigView;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.config.PlatformConfigKey;
import site.hexaarch.ecommerce.logistics.domain.config.entity.SystemSetting;
import site.hexaarch.ecommerce.logistics.domain.config.repository.SystemSettingRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 平台系统配置服务测试（docs/tech/security-design.md §9 P1-a、P1-c）。
 * <p>
 * 用内存假仓储而不是 Mockito：这个服务的价值全在「校验后再整批落库」的顺序上，
 * 假仓储能真实回答"校验失败时库里到底有没有残留半份配置"，而 mock 只能验证调用次数。
 * </p>
 *
 * @author kenyon
 */
class SystemConfigApplicationServiceTest {

    private static final String ADMIN = "platform-admin";

    private InMemoryRepository repository;
    private SystemConfigApplicationService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRepository();
        service = new SystemConfigApplicationService(repository);
    }

    @Test
    @DisplayName("空库读取时按目录默认值回填，12 项齐全")
    void defaultsAreBackfilledFromCatalog() {
        SystemConfigView view = service.current();

        assertEquals("跨境物流ERP", view.getGeneral().getSiteName());
        assertEquals(587, view.getEmail().getSmtpPort().intValue());
        assertFalse(view.getEmail().getEnabled());
        assertEquals(86_400, view.getSecurity().getSessionTimeout().intValue());
        assertEquals(5, view.getSecurity().getMaxLoginAttempts().intValue());
        assertFalse(view.getSecurity().getTwoFactorAuthRequired());
        assertTrue(view.getSecurity().getPasswordPolicyEnabled());
        assertTrue(repository.stored.isEmpty(), "读默认值不该顺手写库，否则首次 GET 就产生脏更新记录");
    }

    @Test
    @DisplayName("保存后读回生效值，并留下修改人与分组")
    void updatePersistsEveryCatalogKey() {
        SystemConfigView requested = validView();
        requested.getGeneral().setSiteName("  测试站点  ");
        requested.getSecurity().setSessionTimeout(3600);

        SystemConfigView saved = service.update(requested, ADMIN);

        assertEquals("测试站点", saved.getGeneral().getSiteName(), "首尾空白应被裁掉");
        assertEquals(3600, saved.getSecurity().getSessionTimeout().intValue());
        assertEquals(PlatformConfigKey.values().length, repository.stored.size());
        for (SystemSetting setting : repository.stored.values()) {
            assertEquals(ADMIN, setting.getUpdatedBy());
            assertEquals(setting.getKey().substring(0, setting.getKey().indexOf('.')), setting.getGroup());
            assertTrue(setting.getUpdatedAt() != null);
        }
    }

    @Test
    @DisplayName("校验不过时一项都不落库（不允许出现半存状态）")
    void validationFailureLeavesStoreUntouched() {
        SystemConfigView requested = validView();
        requested.getGeneral().setSiteName("   ");

        assertThrows(ValidationException.class, () -> service.update(requested, ADMIN));
        assertTrue(repository.stored.isEmpty(), "空站点名应在写库前就被拒，不能留下半份配置");

        SystemConfigView badEmail = validView();
        badEmail.getGeneral().setContactEmail("not-an-email");
        assertThrows(ValidationException.class, () -> service.update(badEmail, ADMIN));
        assertTrue(repository.stored.isEmpty());
    }

    @Test
    @DisplayName("会话超时与锁定阈值有硬边界")
    void numericBoundsAreEnforced() {
        for (int illegal : List.of(299, 0, -1)) {
            SystemConfigView view = validView();
            view.getSecurity().setSessionTimeout(illegal);
            assertThrows(ValidationException.class, () -> service.update(view, ADMIN),
                    "会话超时 " + illegal + " 秒应被拒（下限 " + SystemConfigApplicationService.SESSION_TIMEOUT_MIN_SECONDS + "）");
        }
        SystemConfigView tooLong = validView();
        tooLong.getSecurity().setSessionTimeout(SystemConfigApplicationService.SESSION_TIMEOUT_MAX_SECONDS + 1);
        assertThrows(ValidationException.class, () -> service.update(tooLong, ADMIN));

        SystemConfigView attemptsTooHigh = validView();
        attemptsTooHigh.getSecurity().setMaxLoginAttempts(SystemConfigApplicationService.MAX_LOGIN_ATTEMPTS_UPPER_BOUND + 1);
        assertThrows(ValidationException.class, () -> service.update(attemptsTooHigh, ADMIN));

        // 0 是「关闭锁定」的合法取值，不能被下限挡住
        SystemConfigView attemptsZero = validView();
        attemptsZero.getSecurity().setMaxLoginAttempts(0);
        assertEquals(0, service.update(attemptsZero, ADMIN).getSecurity().getMaxLoginAttempts().intValue());
    }

    @Test
    @DisplayName("开启双因素认证被明确拒绝：后端没有 TOTP 链路，勾上等于假安全感")
    void twoFactorEnablementIsRejected() {
        SystemConfigView view = validView();
        view.getSecurity().setTwoFactorAuthRequired(true);

        ValidationException exception = assertThrows(ValidationException.class, () -> service.update(view, ADMIN));
        assertTrue(exception.getMessage().contains("TOTP"), exception.getMessage());
        assertTrue(repository.stored.isEmpty());
    }

    @Test
    @DisplayName("端口必填且要在 TCP 合法区间")
    void smtpPortIsValidated() {
        SystemConfigView view = validView();
        view.getEmail().setSmtpPort(null);
        assertThrows(ValidationException.class, () -> service.update(view, ADMIN));

        SystemConfigView badPort = validView();
        badPort.getEmail().setSmtpPort(70000);
        assertThrows(ValidationException.class, () -> service.update(badPort, ADMIN));
    }

    @Test
    @DisplayName("生效值读取：会话超时与锁定阈值取自库里的值")
    void effectiveValuesComeFromStore() {
        SystemConfigView view = validView();
        view.getSecurity().setSessionTimeout(1800);
        view.getSecurity().setMaxLoginAttempts(3);
        view.getSecurity().setPasswordPolicyEnabled(false);
        service.update(view, ADMIN);

        assertEquals(1800, service.sessionTimeoutSeconds());
        assertEquals(3, service.maxLoginAttempts());
        assertFalse(service.isPasswordPolicyEnabled());
    }

    @Test
    @DisplayName("库里存了脏值时退回默认而不是抛异常（不能让配置页把平台打挂）")
    void corruptStoredValueFallsBackToDefault() {
        repository.stored.put(PlatformConfigKey.SESSION_TIMEOUT.getKey(),
                SystemSetting.of(PlatformConfigKey.SESSION_TIMEOUT, "not-a-number", "x"));
        repository.stored.put(PlatformConfigKey.PASSWORD_POLICY_ENABLED.getKey(),
                SystemSetting.of(PlatformConfigKey.PASSWORD_POLICY_ENABLED, "maybe", "x"));

        assertEquals(86_400, service.sessionTimeoutSeconds());
        assertTrue(service.isPasswordPolicyEnabled());
    }

    @Test
    @DisplayName("密码策略开启时要求至少 8 位，关闭后按原样放行")
    void passwordPolicyGate() {
        assertThrows(ValidationException.class, () -> service.requireAcceptablePassword("1234567"));
        assertThrows(ValidationException.class, () -> service.requireAcceptablePassword(null));
        service.requireAcceptablePassword("Smoke#123456");

        SystemConfigView off = validView();
        off.getSecurity().setPasswordPolicyEnabled(false);
        service.update(off, ADMIN);

        service.requireAcceptablePassword("123456");
    }

    /**
     * 这个方向是刻意的：如果缺字段就回默认值，那么一次只带 general 的 PUT
     * 会把安全组（会话超时、锁定阈值、密码策略）静默重置成出厂值 —— 比报错危险得多。
     */
    @Test
    @DisplayName("只传一部分分组时拒绝，而不是把没传的组静默重置成默认值")
    void partialPayloadIsRejectedInsteadOfSilentlyReset() {
        SystemConfigView partial = new SystemConfigView();
        partial.getGeneral().setSiteName("仅填了站点名");

        ValidationException exception = assertThrows(ValidationException.class,
                () -> service.update(partial, ADMIN));
        assertTrue(exception.getMessage().contains("不能为空"),
                "要指出缺哪个必填项，而不是含糊的「参数错误」: " + exception.getMessage());
        assertTrue(repository.stored.isEmpty());
    }

    private static SystemConfigView validView() {
        SystemConfigView view = new SystemConfigView();
        view.getGeneral().setSiteName("跨境物流ERP");
        view.getGeneral().setSiteDescription("综合电商物流ERP管理系统");
        view.getGeneral().setContactEmail("admin@example.com");
        view.getGeneral().setContactPhone("400-123-4567");
        view.getEmail().setSmtpHost("smtp.example.com");
        view.getEmail().setSmtpPort(587);
        view.getEmail().setSenderEmail("noreply@example.com");
        view.getEmail().setEnabled(true);
        view.getSecurity().setSessionTimeout(86_400);
        view.getSecurity().setMaxLoginAttempts(5);
        view.getSecurity().setTwoFactorAuthRequired(false);
        view.getSecurity().setPasswordPolicyEnabled(true);
        return view;
    }

    /**
     * 按键幂等的内存仓储假实现，行为与 {@code SystemSettingRepositoryImpl} 的 upsert 一致。
     */
    private static final class InMemoryRepository implements SystemSettingRepository {

        private final Map<String, SystemSetting> stored = new LinkedHashMap<>();

        @Override
        public List<SystemSetting> findAll() {
            return new ArrayList<>(stored.values());
        }

        @Override
        public SystemSetting save(SystemSetting setting) {
            stored.put(setting.getKey(), setting);
            return setting;
        }
    }
}
