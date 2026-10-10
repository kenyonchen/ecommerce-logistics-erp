package site.hexaarch.ecommerce.logistics.architecture;

import jakarta.persistence.Column;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 金额精度的架构守卫（ISS-023 / BR-18 / R-52）。
 * <p>
 * 盯的是"金额走 double"这一类不会报错、只会在数据上留脏痕的写法。实测形状：
 * {@code skus.price} 曾映射成 {@code Double}，76 行商品的价格求和后回显成
 * {@code 828.399999999999}（应为 {@code 828.40}）——列已经是 {@code DECIMAL} 也救不回来，
 * 因为损失发生在 JDBC→Java 的那一跳；而 {@code price_per_gram} 这类四位小数费率经
 * {@code Double} 会把 {@code 0.0050} 变成 {@code 0.005}，乘上克重后再补成 BigDecimal，
 * 噪声已经进了库。
 * </p>
 * <p>
 * 因此本条口径分三段：① JPA 实体里金额名次的字段<b>类型</b>不得是 {@code Double}/{@code double}
 * （含 {@code java.lang.Double} 这种全限定写法——本仓确实存在全限定声明，见
 * {@code PaymentInfoJpaEntity} 的 {@code java.math.BigDecimal}），且扫描器有反向自证，
 * 避免"正则失配所以永远绿"；② 全部 15 个金额字段必须显式声明 {@code precision}/{@code scale}——
 * 否则 Hibernate 在 {@code ddl-auto=update} 下按默认宽度建列，那正是 {@code purchase_*}
 * 与财务表曾是 {@code DECIMAL(38,2)} 的成因；③ 映射层不得留
 * {@code doubleValue()}/{@code BigDecimal.valueOf(金额Double)} 这类"先降精度再补回来"的中转代码。
 * </p>
 * <p>
 * 刻意不在本条口径内的只有<b>重量</b>（{@code skus.weight}、{@code logistics_labels.package_weight}）：
 * 它不是货币，{@code docs/db/migration-plan.md} §4 只约束金额列，领域模型本身也是 {@code double}
 * （R-52）。V7 收"浮点金额"、V7.2 收"同一语义两种 DECIMAL 精度"，数据字典 R-09 的两半由此一起闭。
 * </p>
 *
 * @author kenyon
 */
class MoneyPrecisionGuardTest {

    /**
     * JPA 实体源码目录（相对模块根 {@code backend/}）。
     */
    private static final String ENTITY_SOURCE_DIR =
            "src/main/java/site/hexaarch/ecommerce/logistics/infrastructure/persistence/jpa/entity";

    /**
     * 映射器源码目录：金额只允许同类型直传，出现中转换算即为精度回归。
     */
    private static final String MAPPER_SOURCE_DIR =
            "src/main/java/site/hexaarch/ecommerce/logistics/infrastructure/persistence/jpa/mapper";

    /**
     * 金额名次：货币语义的词根集合，重量/体积不在其中。
     */
    private static final Pattern MONEY_NAME = Pattern.compile(
            "(?i).*(amount|price|fee|cost|money|balance|payment|refund).*");

    /**
     * 实体源码里的定点金额字段声明（两种写法都算：{@code BigDecimal} 与全限定 {@code java.math.BigDecimal}）。
     */
    private static final Pattern DECIMAL_FIELD = Pattern.compile(
            "^\\s*(?:private|protected|public)\\s+(?:java\\.math\\.)?BigDecimal\\s+(\\w+)\\s*(?:=[^;]*)?;");

    /**
     * 形如 {@code private Double totalAmount;} 的浮点金额字段声明。
     * 类型名允许 {@code java.lang.Double} 全限定写法，与本仓 BigDecimal 的两种写法对称。
     */
    private static final Pattern FLOAT_FIELD = Pattern.compile(
            "^\\s*(?:private|protected|public)\\s+(?:java\\.lang\\.)?(?:Double|double)\\s+(\\w+)\\s*(?:=[^;]*)?;");

    /**
     * 金额被降成 double 的第一种写法：对金额 getter 调 {@code doubleValue()}。
     */
    private static final Pattern MONEY_DOUBLE_HOP = Pattern.compile(
            "(?i)(?:amount|price|fee|cost|money|balance|payment|refund)\\w*(?:\\(\\))?\\.doubleValue\\(\\)");

    /**
     * 金额被降成 double 的第二种写法：拿一个金额 {@code Double} 去 {@code BigDecimal.valueOf()} 补回来。
     */
    private static final Pattern MONEY_VALUEOF_HOP = Pattern.compile(
            "(?i)BigDecimal\\.valueOf\\s*\\([^)]*(?:amount|price|fee|cost|money|balance|payment|refund)");

    /**
     * 全仓 15 个金额字段的列宽名册：实体 -> (字段名 -> 期望 scale)，precision 统一 19。
     * <p>
     * 这份名册是"逐聚合一位"（R-44）在金额维度上的落法：少一个字段，就意味着库里有一列
     * 由 Hibernate 按默认宽度建出来；多一个字段而实体未改，测试直接红。
     */
    private static final Map<Class<?>, Map<String, Integer>> DECIMAL_SPEC = Map.of(
            site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.order.OrderJpaEntity.class,
            Map.of("totalAmount", 2),
            site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.order
                    .OrderItemJpaEntity.class,
            Map.of("unitPrice", 2, "totalPrice", 2),
            site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.order
                    .PaymentInfoJpaEntity.class,
            Map.of("amount", 2),
            site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.product.SkuJpaEntity.class,
            Map.of("sellingPrice", 2, "costPrice", 2),
            site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.purchase
                    .PurchaseOrderJpaEntity.class,
            Map.of("totalAmount", 2),
            site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.purchase
                    .PurchaseOrderItemJpaEntity.class,
            Map.of("unitPrice", 2, "totalPrice", 2),
            site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.finance
                    .FinancialTransactionJpaEntity.class,
            Map.of("amount", 2),
            site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.logistics
                    .LogisticsFeeJpaEntity.class,
            Map.of("baseFee", 2, "weightFee", 2, "totalFee", 2),
            site.hexaarch.ecommerce.logistics.infrastructure.persistence.jpa.entity.logistics
                    .LogisticsChannelJpaEntity.class,
            Map.of("basePrice", 2, "pricePerGram", 4));

    /**
     * {@code DECIMAL_SPEC} 覆盖的字段数；与 {@code smoke.sh} §14 的 14.0 列名册一一对应。
     */
    private static final int MONEY_FIELD_COUNT = 15;

    @Test
    @DisplayName("JPA 实体的金额字段不得是 Double/double")
    void jpaEntitiesNeverDeclareMoneyAsFloatingPoint() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : javaSources(ENTITY_SOURCE_DIR)) {
            List<String> lines = Files.readAllLines(source);
            for (int index = 0; index < lines.size(); index++) {
                String violation = floatMoneyDeclaration(lines.get(index));
                if (violation != null) {
                    violations.add(source.getFileName() + ":" + (index + 1) + " " + violation);
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> "这些金额字段仍经 double，DECIMAL 列也保不住分位"
                + "（修复前实测产物：商品总价回显 828.399999999999）：\n" + String.join("\n", violations));
    }

    /**
     * 反向自证：上一条必须真能拦住东西，而不是因为正则失配而永远为空。
     */
    @Test
    @DisplayName("浮点金额扫描器确实拦得住，且不误报重量")
    void floatMoneyScannerRejectsRealViolations() {
        assertNotNull(floatMoneyDeclaration("private Double totalAmount;"));
        assertNotNull(floatMoneyDeclaration("    private double unitPrice = 0D;"));
        assertNotNull(floatMoneyDeclaration("private Double pricePerGram;"));
        assertNull(floatMoneyDeclaration("private Double weightInGrams;"), "重量不是货币，不该被本条口径拦住");
        assertNull(floatMoneyDeclaration("private Double packageWeight;"));
        assertNull(floatMoneyDeclaration("private BigDecimal totalAmount;"), "已改成 BigDecimal 不该再报");
        assertNull(floatMoneyDeclaration("// private Double totalAmount;"), "注释里的示例不是字段声明");
        // 本仓有全限定写法（PaymentInfoJpaEntity 的 java.math.BigDecimal），漏了就等于扫描器看不见那一列
        assertNotNull(floatMoneyDeclaration("private java.lang.Double totalAmount;"));
    }

    /**
     * 反向自证：定点金额名次的识别对两种写法都要认，且不把重量算进来。
     */
    @Test
    @DisplayName("定点金额字段识别拦得住（重量不误报）")
    void decimalMoneyFieldScannerRecognisesBothSpellings() {
        assertEquals("totalAmount", decimalMoneyFieldName("private BigDecimal totalAmount;"));
        assertEquals("amount", decimalMoneyFieldName("private java.math.BigDecimal amount;"));
        assertEquals("sellingPrice", decimalMoneyFieldName("    private BigDecimal sellingPrice;"));
        assertNull(decimalMoneyFieldName("private BigDecimal weightInGrams;"), "重量不是货币");
        assertNull(decimalMoneyFieldName("// private BigDecimal totalAmount;"), "注释里的示例不是字段声明");
    }

    @Test
    @DisplayName("全部 15 个金额字段是 BigDecimal 且显式声明 DECIMAL 宽度")
    void everyMoneyFieldDeclaresDecimalWidth() throws NoSuchFieldException, IOException {
        // 名册本身也要被覆盖检：新增一个金额字段却不进名册时，只查名册会一路绿灯。
        long moneyFieldsInSources = javaSources(ENTITY_SOURCE_DIR).stream()
                .flatMap(path -> {
                    try {
                        return Files.readAllLines(path).stream();
                    } catch (IOException e) {
                        throw new IllegalStateException("读取源码失败: " + path, e);
                    }
                })
                .map(String::trim)
                .filter(line -> !line.startsWith("//") && !line.startsWith("*"))
                .map(MoneyPrecisionGuardTest::decimalMoneyFieldName)
                .filter(java.util.Objects::nonNull)
                .count();
        assertEquals(MONEY_FIELD_COUNT, moneyFieldsInSources,
                "实体里金额名次的 BigDecimal 字段数与列宽名册不等：新增金额列必须同时进 "
                        + "DECIMAL_SPEC 与 smoke.sh §14 的 MONEY_COLUMNS，否则它会按 Hibernate 默认宽度建列");

        int checked = 0;
        for (Map.Entry<Class<?>, Map<String, Integer>> entity : DECIMAL_SPEC.entrySet()) {
            for (Map.Entry<String, Integer> moneyField : entity.getValue().entrySet()) {
                Field field = entity.getKey().getDeclaredField(moneyField.getKey());
                assertEquals(BigDecimal.class, field.getType(),
                        entity.getKey().getSimpleName() + "." + moneyField.getKey() + " 的类型");
                Column column = field.getAnnotation(Column.class);
                assertTrue(column != null && column.precision() == 19 && column.scale() == moneyField.getValue(),
                        entity.getKey().getSimpleName() + "." + moneyField.getKey()
                                + " 必须声明 precision=19, scale=" + moneyField.getValue()
                                + "，否则 ddl-auto=update 会按默认宽度建列。实际为 "
                                + (column == null ? "无 @Column" : column.precision() + "," + column.scale()));
                checked++;
            }
        }
        assertEquals(MONEY_FIELD_COUNT, checked,
                "金额字段名册应为 " + MONEY_FIELD_COUNT + " 个，少一个就是列类型与 Java 类型不一致");
    }

    @Test
    @DisplayName("映射层不留金额 double 中转换算")
    void mappersNeverNarrowMoneyThroughDouble() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : javaSources(MAPPER_SOURCE_DIR)) {
            List<String> lines = Files.readAllLines(source);
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index).trim();
                if (line.startsWith("//") || line.startsWith("*")) {
                    continue;
                }
                if (MONEY_DOUBLE_HOP.matcher(line).find() || MONEY_VALUEOF_HOP.matcher(line).find()) {
                    violations.add(source.getFileName() + ":" + (index + 1) + " " + line);
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> "映射层又把金额过了一遍 double：\n" + String.join("\n", violations));
    }

    /**
     * 反向自证：中转换算扫描器对真实违规形状必须报，对重量形状必须不报。
     */
    @Test
    @DisplayName("金额中转扫描器确实拦得住（重量不误报）")
    void moneyConversionScannerRejectsRealViolations() {
        assertTrue(narrowsMoney("entity.setTotalFee(fee.getTotalFee().doubleValue());"));
        assertTrue(narrowsMoney("entity.setBasePrice(channel.getBasePrice() == null ? null"
                + " : channel.getBasePrice().doubleValue());"));
        assertTrue(narrowsMoney(".pricePerGram(BigDecimal.valueOf(entity.getPricePerGram()))"));
        assertTrue(narrowsMoney("dto.setUnitPrice(item.getUnitPrice().doubleValue());"));
        assertFalse(narrowsMoney("return value != null ? value.doubleValue() : null;"), "无金额名的通用换算不该报");
        assertFalse(narrowsMoney("double grams = weight.doubleValue();"), "重量不是货币");
        assertFalse(narrowsMoney("entity.setPackageWeight(label.getWeight());"));
    }

    private static boolean narrowsMoney(String line) {
        return MONEY_DOUBLE_HOP.matcher(line).find() || MONEY_VALUEOF_HOP.matcher(line).find();
    }

    /**
     * 命中即返回可打印的违规行；注释与不匹配货币名次的声明返回 null。
     */
    private static String floatMoneyDeclaration(String line) {
        Matcher matcher = FLOAT_FIELD.matcher(line);
        if (!matcher.find() || !MONEY_NAME.matcher(matcher.group(1)).matches()) {
            return null;
        }
        return line.trim();
    }

    /**
     * 定点金额字段名；非金额名次（重量等）与非声明行返回 null。
     */
    private static String decimalMoneyFieldName(String line) {
        Matcher matcher = DECIMAL_FIELD.matcher(line);
        if (!matcher.find() || !MONEY_NAME.matcher(matcher.group(1)).matches()) {
            return null;
        }
        return matcher.group(1);
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
    private static Path resolveDir(String relativeDir) {
        Path fromModuleRoot = Path.of(relativeDir);
        if (Files.isDirectory(fromModuleRoot)) {
            return fromModuleRoot;
        }
        Path fromRepoRoot = Path.of("backend", relativeDir);
        assertTrue(Files.isDirectory(fromRepoRoot),
                "找不到源码目录（试过 " + fromModuleRoot + " 与 " + fromRepoRoot + "），守卫需在仓库内运行");
        return fromRepoRoot;
    }
}
