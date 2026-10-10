package site.hexaarch.ecommerce.logistics.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 写路径租户归属的架构守卫（ISS-118 / R-30）。
 * <p>
 * 盯的是这样一个形态：应用服务在 {@code tenantId} 缺省时直接写
 * {@code tenantId == null ? "default-tenant" : tenantId}。字面上"有个兜底值"看着无害，
 * 但 {@code default-tenant} 在 {@code tenants} 表里<b>没有对应行</b>，是个幻影桶——
 * 按它落库的客户、金额记录、仓库，登录租户自己的列表按租户收敛后一条都看不见，
 * 导入"成功"、创建"200"，数据却进了谁也取不出来的桶（冒烟 6.2c 与本轮真 HTTP 探针实测）。
 * </p>
 * <p>
 * 正确的写法只有一个入口：{@code TenantAccessGuard.resolveWriteTenant(requested, legacyFallback)}。
 * 它在显式传参时校验跨租户、在缺省时取登录租户，只有完全没有会话时才落到历史默认桶。
 * 该守卫把"绕过入口自己兜底"变成测试里的红，因为这类代码漏一次不会报错，只会在数据上留下
 * 一条永久查不出来源的脏记录。
 * </p>
 * <p>
 * 范围：只扫 {@code application/service} 目录——真正构造聚合、决定归属的那一层。
 * 控制器里的 {@code resolveTenant} 是请求侧适配（已先取 TenantContext），
 * {@code infrastructure} 与事件消费者没有会话上下文，都不在本条口径内，
 * 已在 known-issues §13 R-30 里按残留登记。
 * </p>
 *
 * @author kenyon
 */
class TenantWriteFallbackGuardTest {

    /**
     * 应用服务源码目录（相对模块根 {@code backend/}）。
     */
    private static final String SERVICE_SOURCE_DIR = "src/main/java/site/hexaarch/ecommerce/logistics/application/service";

    /**
     * 唯一允许的租户归属入口。
     */
    private static final String GUARD_CALL = "resolveWriteTenant(";

    private static final String LEGACY_BUCKET = "default-tenant";

    @Test
    @DisplayName("应用服务不得绕过 resolveWriteTenant 自己兜底到历史默认桶")
    void applicationServicesNeverBypassTheTenantWriteGuard() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : serviceSources()) {
            List<String> lines = Files.readAllLines(source);
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                if (!mentionsLegacyBucket(line) || isAllowed(line)) {
                    continue;
                }
                violations.add(source.getFileName() + ":" + (index + 1) + " " + line.trim());
            }
        }
        assertTrue(violations.isEmpty(), () -> "这些写路径没有走 TenantAccessGuard.resolveWriteTenant，"
                + "缺省 tenantId 时会把数据落进没有租户行的 default-tenant 幻影桶：\n" + String.join("\n", violations));
    }

    /**
     * 反向钉住"守卫真的在被用"：把兜底全删空也能让上一条测试变绿，所以这里要求入口调用数不低于阈值。
     */
    @Test
    @DisplayName("resolveWriteTenant 在应用服务里被真实使用")
    void tenantWriteGuardIsActuallyWiredIntoApplicationServices() throws IOException {
        long callSites = serviceSources().stream()
                .flatMap(source -> {
                    try {
                        return Files.readAllLines(source).stream();
                    } catch (IOException e) {
                        throw new IllegalStateException("读取源码失败: " + source, e);
                    }
                })
                .filter(line -> line.contains(GUARD_CALL))
                .count();
        assertTrue(callSites >= 15,
                "应用服务里 resolveWriteTenant 调用点仅 " + callSites + " 处，租户归属入口疑似被批量移除");
    }

    private static boolean mentionsLegacyBucket(String line) {
        return line.contains(LEGACY_BUCKET) || line.contains("DEFAULT_TENANT_ID");
    }

    private static boolean isAllowed(String line) {
        String trimmed = line.trim();
        boolean comment = trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*");
        boolean declaration = trimmed.contains("String DEFAULT_TENANT_ID")
                // 守卫自己持有的那份是"幻影桶"这个概念的正典常量（读侧归一化要用它），不是兜底分支
                || trimmed.contains("String LEGACY_TENANT_ID");
        boolean throughGuard = trimmed.contains(GUARD_CALL);
        return comment || declaration || throughGuard;
    }

    private static List<Path> serviceSources() throws IOException {
        Path dir = resolveServiceDir();
        try (var stream = Files.walk(dir)) {
            List<Path> sources = stream
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .collect(Collectors.toList());
            assertFalse(sources.isEmpty(), dir + " 下没有 Java 源文件");
            return sources;
        }
    }

    /**
     * Surefire 的工作目录是模块根；从仓库根跑测试时兼容一层 {@code backend/} 前缀。
     */
    private static Path resolveServiceDir() {
        Path fromModuleRoot = Path.of(SERVICE_SOURCE_DIR);
        if (Files.isDirectory(fromModuleRoot)) {
            return fromModuleRoot;
        }
        Path fromRepoRoot = Path.of("backend", SERVICE_SOURCE_DIR);
        assertTrue(Files.isDirectory(fromRepoRoot),
                "找不到应用服务源码目录（试过 " + fromModuleRoot + " 与 " + fromRepoRoot + "），守卫需在仓库内运行");
        return fromRepoRoot;
    }
}
