package site.hexaarch.ecommerce.logistics.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 缺货补货的收敛守卫（ISS-143）。
 * <p>
 * 缺陷的形状是<b>消费端各写一套</b>：进程内 {@code EventHandler} 与 MQ {@code InventoryShortageEventConsumer}
 * 都直接调 4 参重载建"只有单头"的采购单，事件里带的 SKU／现存量／阈值从未变成明细行。
 * 实测 366 张 {@code DEFAULT_SUPPLIER} 自动单里 366 张的 {@code purchase_order_items} 计数为 0，
 * 而 BR-09 的到货入库是逐行 {@code inventoryIn} ⇒ 空单到货什么都入不了，BR-12 这条链不可用。
 * </p>
 * <p>
 * 所以这里守的不是某一行的写法，而是"补货语义只有一个入口"：任何消费 {@code InventoryShortageEvent} 的
 * 新通道（新增 MQ topic、定时补偿任务、导入回滚）都必须走 {@code replenishForShortage}，
 * 绕过它就变红 —— 与 {@code WarehouseReferenceGuardTest}、{@code FreightPostingGuardTest} 同一条思路（R-44）。
 * </p>
 *
 * @author kenyon
 */
class ShortageReplenishmentGuardTest {

    private static final String MAIN_SOURCE_DIR = "src/main/java/site/hexaarch/ecommerce/logistics";

    private static final String REPLENISHMENT_SERVICE =
            MAIN_SOURCE_DIR + "/application/service/purchase/ReplenishmentApplicationService.java";

    /**
     * 共用方法名：消费端唯一允许的入口。
     */
    private static final String SHARED_ENTRY = "replenishForShortage(";

    /**
     * 目前的事件消费端：进程内 + MQ 两条通道。
     */
    private static final int EXPECTED_CONSUMER_FILES = 2;

    @Test
    @DisplayName("每个缺货事件消费端都必须走共用补货方法，不得自己建单")
    void everyShortageEventConsumerDelegatesToTheSharedEntry() throws IOException {
        List<String> consumerFiles = new ArrayList<>();
        for (Path source : javaSources(MAIN_SOURCE_DIR)) {
            String text = Files.readString(source);
            String fileName = source.getFileName().toString();
            // 事件定义自身、注册事件的仓储聚合、以及共用入口的实现都排除在外
            if (!text.contains("InventoryShortageEvent")
                    || fileName.equals("InventoryShortageEvent.java")
                    || fileName.equals("Warehouse.java")
                    || fileName.contains("ReplenishmentApplicationService")) {
                continue;
            }
            consumerFiles.add(fileName);
            assertTrue(text.contains(SHARED_ENTRY), fileName
                    + " 消费了 InventoryShortageEvent 却没走 " + SHARED_ENTRY
                    + "：补货语义一旦各写一套，就会出现只有单头、没有明细行的采购单（ISS-143）");
            assertTrue(!text.contains("createPurchaseOrder("), fileName
                    + " 直接调 createPurchaseOrder：绕过共用方法就等于绕过定价与明细行，"
                    + "空单进不了 BR-09 的逐行入库");
        }

        assertEquals(EXPECTED_CONSUMER_FILES, consumerFiles.size(),
                "缺货事件的消费端应为 " + EXPECTED_CONSUMER_FILES + " 个（进程内 + MQ），实际 "
                        + String.join("、", consumerFiles)
                        + " —— 新增通道时必须接同一个共用方法，并把这条名册计数抬上去");
    }

    @Test
    @DisplayName("共用入口必须组装明细行并按 5 参重载建单")
    void sharedEntryBuildsItemLinesInsteadOfHeadOnlyOrders() throws IOException {
        String body = replenishmentMethodBody();

        assertTrue(body.contains("PurchaseOrderItem.create("),
                "补货必须组明细行：只有单头的采购单在 BR-09 到货时逐行入库，等于一行都入不了（ISS-143）");
        assertTrue(body.contains("List.of(item)"),
                "建单必须走带明细的 5 参重载；退回 4 参重载就是重新写出那张空单");
        assertTrue(body.contains("MIN_SUGGESTED_QUANTITY"),
                "建议量必须有下限：现存量已达阈值时算出 0，PurchaseOrderItem.create 会抛 ValidationException，"
                        + "整张单被消费端的 catch 吞成一条 error 日志");
    }

    @Test
    @DisplayName("采购单价只能取成本价，不得拿售价顶替")
    void unitPriceComesFromCostPriceOnly() throws IOException {
        String body = replenishmentMethodBody();

        assertTrue(body.contains("getCostPrice()"),
                "补货单价的来源必须是 SKU 成本价");
        assertTrue(!body.contains("getSellingPrice()"),
                "售价不是采购价：拿售价猜一个金额，等于造一张能过审批却金额虚假的账，"
                        + "到货还会按假金额生成应付流水（R-58）");
        assertTrue(body.contains("signum() <= 0"),
                "没有有效成本价时必须只发预警不建单，不能建出零元单");
    }

    @Test
    @DisplayName("补货建单前按事件载荷判租户归属")
    void tenantIsResolvedFromTheEventPayload() throws IOException {
        String body = replenishmentMethodBody();

        assertTrue(body.contains("event.getTenantId()"),
                "租户归属取自事件载荷：MQ 线程没有会话上下文，TenantContext 在这里是空的");
        assertTrue(body.contains("getTenantId()") && body.contains("跳过建单"),
                "商品归属别家租户时必须跳过：事件线程里 TenantAccessGuard 会因为没有会话而放行，"
                        + "越权判定只能按载荷自己判（ISS-050 同口径）");
        assertTrue(!body.contains("TenantContext"),
                "共用入口跑在事件线程上，读会话租户会把 MQ 侧的补货判成无租户；不得依赖 TenantContext");
    }

    private static String replenishmentMethodBody() throws IOException {
        String source = Files.readString(resolveDir(REPLENISHMENT_SERVICE));
        int start = source.indexOf("public Optional<PurchaseOrder> " + SHARED_ENTRY);
        assertTrue(start >= 0, "找不到 replenishForShortage(InventoryShortageEvent) 的签名，改了入参要同步改这条守卫");
        int end = source.indexOf("\n    }", start);
        assertTrue(end > start, "replenishForShortage 方法体没被正确定位，守卫会静默扫到文件末尾");
        return source.substring(start, end);
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
