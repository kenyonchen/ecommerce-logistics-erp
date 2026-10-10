package site.hexaarch.ecommerce.logistics.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 运费链路的架构守卫（ISS-137 / R-55）。
 * <p>
 * ISS-137 的根因不是"少写了一行 setLogisticsFee"，而是<b>计费</b>与<b>落库</b>两条链路各自成立、
 * 中间没有接线：公式只存在于试算端点，持久化侧的 {@code @OneToOne(cascade = ALL)} 与双向映射
 * 却一直齐备，于是 {@code logistics_fees} 恒 0 行。这类"两边都对、中间断线"的缺陷最容易在
 * 后续新增建单路径时再次出现，所以这里钉三条口径：
 * </p>
 * <ol>
 *   <li>公式只有一份（续重单价做乘法的文件只能是 {@code LogisticsChannel}），否则试算与落库会分叉；</li>
 *   <li>每条创建物流单的路径都必须在保存前估一次运费；</li>
 *   <li>计费重只能取 SKU 的克重，不得改用面单重量——{@code logistics_labels.package_weight} 的单位是千克。</li>
 * </ol>
 *
 * @author kenyon
 */
class FreightFormulaGuardTest {

    /**
     * 主源码根目录（相对模块根 {@code backend/}）。
     */
    private static final String MAIN_SOURCE_DIR = "src/main/java/site/hexaarch/ecommerce/logistics";

    /**
     * 物流应用服务：三条创建路径（HTTP 建单、编排建单、批量发货新建）唯一的接线点。
     */
    private static final String LOGISTICS_APPLICATION_SERVICE =
            MAIN_SOURCE_DIR + "/application/service/logistics/LogisticsApplicationService.java";

    /**
     * 建单入口数＝必须估费的入口数。新增一条创建路径却不估费，就是 ISS-137 复发。
     */
    private static final int EXPECTED_ESTIMATE_CALL_SITES = 3;

    @Test
    @DisplayName("运费公式只允许存在一处：续重单价不得在 LogisticsChannel 之外参与乘法")
    void freightFormulaMustNotBeDuplicated() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path source : javaSources(MAIN_SOURCE_DIR)) {
            if (source.getFileName().toString().equals("LogisticsChannel.java")) {
                continue;
            }
            String text = Files.readString(source);
            if (text.contains("ricePerGram") && text.contains("multiply(")) {
                offenders.add(source.getFileName().toString());
            }
        }
        assertTrue(offenders.isEmpty(),
                "运费公式出现分叉，试算端点与建单落库会给出不同金额：" + String.join("、", offenders));
    }

    @Test
    @DisplayName("每条创建物流单的路径都必须在保存前估运费")
    void everyLogisticsOrderCreationPathEstimatesFreight() throws IOException {
        String source = Files.readString(resolveDir(LOGISTICS_APPLICATION_SERVICE));
        long callSites = countOccurrences(source, "estimateFreight(") - 1;

        assertTrue(callSites >= EXPECTED_ESTIMATE_CALL_SITES,
                "LogisticsApplicationService 里估费的调用点应为 " + EXPECTED_ESTIMATE_CALL_SITES
                        + " 处（HTTP 建单 / 编排建单 / 批量发货新建），实际 " + callSites
                        + " 处 —— 少一条路径就等于那条路径的运费永远是空（ISS-137）");
    }

    @Test
    @DisplayName("计费重取 SKU 克重，不得改用面单千克重量")
    void freightWeightComesFromSkuGramsNotLabelKilograms() throws IOException {
        String source = Files.readString(resolveDir(LOGISTICS_APPLICATION_SERVICE));

        assertTrue(source.contains("getWeightInGrams"),
                "计费重必须来自 SKU 的 weightInGrams（与 price_per_gram 同为克）");
        assertTrue(!source.contains(".getWeight()"),
                "logistics_labels.package_weight 的单位是千克（LogisticsLabel.PackageInfo 默认 weightUnit=kg），"
                        + "用它当克计费会把运费缩小 1000 倍");
    }

    private static long countOccurrences(String text, String needle) {
        long count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static List<Path> javaSources(String relativeDir) throws IOException {
        Path dir = resolveDir(relativeDir);
        try (var stream = Files.walk(dir)) {
            List<Path> sources = stream
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .collect(Collectors.toList());
            assertTrue(!sources.isEmpty(), dir + " 下没有 Java 源文件");
            return sources;
        }
    }

    /**
     * Surefire 的工作目录是模块根；从仓库根跑测试时兼容一层 {@code backend/} 前缀。
     */
    private static Path resolveDir(String relativePath) {
        Path fromModuleRoot = Path.of(relativePath);
        if (Files.exists(fromModuleRoot)) {
            return fromModuleRoot;
        }
        Path fromRepoRoot = Path.of("backend", relativePath);
        assertTrue(Files.exists(fromRepoRoot), "找不到源文件：" + relativePath);
        return fromRepoRoot;
    }
}
