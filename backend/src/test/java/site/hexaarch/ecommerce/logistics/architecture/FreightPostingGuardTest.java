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
 * 运费入账链路的架构守卫（BR-10 / R-58）。
 * <p>
 * ISS-137 的教训是"计费与落库两条链路各自成立、中间没接线"；BR-10 是它的续集：
 * 运费进了 {@code logistics_fees}，却仍然没有进 {@code financial_transactions}
 * ——{@code LogisticsCompletedEventConsumer} 只留了一行"记账规则未定义"的日志。
 * 入账这种"每个发货入口都要顺带做一次"的副作用，最容易在未来新增一条建单/发货路径时漏掉，
 * 而漏掉的表现是<b>台账少一行</b>，接口照样 200，靠人眼看不出来。这里钉死四条口径：
 * </p>
 * <ol>
 *   <li>入账调用点数必须跟得住发货入口数（每多一条 {@code markShipped} 门就多一个入账点）；</li>
 *   <li>入账金额只读已落库的 {@code totalFee}，绝不在这条链路上重算（渠道改价不得拖动历史应付）；</li>
 *   <li>幂等判据（同单同类流水只记一条）不能被后来的"顺手去掉那个 if"抹掉；</li>
 *   <li>关联单据类型 {@code LOGISTICS_ORDER} 全库只有一个定义处，且入账只能经财务应用服务，
 *       不得绕到仓库直写（绕开金额与租户校验）。</li>
 * </ol>
 *
 * @author kenyon
 */
class FreightPostingGuardTest {

    /**
     * 主源码根目录（相对模块根 {@code backend/}）。
     */
    private static final String MAIN_SOURCE_DIR = "src/main/java/site/hexaarch/ecommerce/logistics";

    private static final String LOGISTICS_APPLICATION_SERVICE =
            MAIN_SOURCE_DIR + "/application/service/logistics/LogisticsApplicationService.java";

    private static final String FINANCE_APPLICATION_SERVICE =
            MAIN_SOURCE_DIR + "/application/service/finance/FinanceApplicationService.java";

    /**
     * 编排建单重载的方法签名：这条只建单不发货，是唯一必须"不入账"的创建路径。
     */
    private static final String ORCHESTRATION_CREATE_SIGNATURE =
            "public LogisticsOrder createLogisticsOrder(String tenantId";

    /**
     * 入账调用点数＝发货入口数：手工揽收、批量发货、建单即发货、状态推进到"货已交出"。
     */
    private static final int EXPECTED_POSTING_CALL_SITES = 4;

    @Test
    @DisplayName("每个发货入口都必须入账，漏一条就是台账少一行且无人报错")
    void everyShipmentPathPostsFreight() throws IOException {
        String source = Files.readString(resolveDir(LOGISTICS_APPLICATION_SERVICE));
        long postingCallSites = countOccurrences(source, "postFreightExpense(") - 1;
        long collectionCallSites = countOccurrences(source, "markShipped(");

        assertTrue(postingCallSites >= EXPECTED_POSTING_CALL_SITES,
                "入账调用点应为 " + EXPECTED_POSTING_CALL_SITES + " 处（手工揽收 / 批量发货 / 建单即发货 / "
                        + "状态推进到已揽收及之后），实际 " + postingCallSites + " 处");
        // 揽收门与入账点必须成对出现：新增一条 markShipped 却不调 postFreightExpense，
        // 就是 BR-10 复发（状态推进端点额外贡献一个入账点，所以这里用 >= 而不是 ==）
        assertTrue(postingCallSites >= collectionCallSites,
                "有 " + collectionCallSites + " 处 markShipped 却只有 " + postingCallSites
                        + " 处入账调用：这条揽收路径的运费不会进财务台账");
    }

    @Test
    @DisplayName("入账只读已落库的运费，不得在入账链路上重算")
    void freightPostingNeverRecomputesTheFee() throws IOException {
        String source = Files.readString(resolveDir(LOGISTICS_APPLICATION_SERVICE));

        assertEquals(1, countOccurrences(source, "estimateFee("),
                "全服务只允许计费点调用一次渠道公式（estimateFreight 内部），"
                        + "入账链路再算一次会让渠道改价拖动历史应付（R-55 / R-58）");
        assertEquals(0, countOccurrences(source, "pricePerGram"),
                "续重单价不得出现在物流应用服务里：公式归 LogisticsChannel 一处");
        assertTrue(source.contains("fee.getTotalFee()"),
                "入账金额必须直接取已落库的 totalFee");
    }

    @Test
    @DisplayName("同单同类流水只记一条：幂等判据不能被抹掉")
    void postingIsGuardedByIdempotencyCheck() throws IOException {
        String source = Files.readString(resolveDir(LOGISTICS_APPLICATION_SERVICE));

        assertTrue(source.contains("existsFinancialTransaction("),
                "入账前必须查「这张物流单是否已有 EXPENSE 流水」（BR-03 幂等判据）；"
                        + "重复发货、发货后再推进状态都不能记第二笔");
    }

    @Test
    @DisplayName("编排建单只建单不发货，必须保持不入账")
    void orchestrationCreatePathStaysUnposted() throws IOException {
        String source = Files.readString(resolveDir(LOGISTICS_APPLICATION_SERVICE));
        int start = source.indexOf(ORCHESTRATION_CREATE_SIGNATURE);
        assertTrue(start >= 0, "找不到编排建单重载，签名变了要同步改这条守卫");
        int end = source.indexOf("\n    }\n", start);
        String methodBody = source.substring(start, end);

        assertTrue(!methodBody.contains("postFreightExpense("),
                "事件/编排链的自动建单不能入账：货没交出去就记应付，等于凭空造成本");
        assertTrue(!methodBody.contains("markShipped("),
                "自动建单如果开始揽收，就得同步加入账调用点，并把 EXPECTED_POSTING_CALL_SITES 一起抬上去");
    }

    @Test
    @DisplayName("运费流水的关联单据类型全库只有一个定义处")
    void freightReferenceTypeHasSingleSourceOfTruth() throws IOException {
        // 字面量的家从物流应用服务搬到了财务应用服务（ISS-139）：入账与撤销两个方向都要引用它，
        // 定义处必须是台账这一侧，否则撤销侧要么各写一份字面量、要么反向依赖物流模块。
        List<String> offenders = new ArrayList<>();
        for (Path source : javaSources(MAIN_SOURCE_DIR)) {
            String text = Files.readString(source);
            if (text.contains("\"LOGISTICS_ORDER\"")
                    && !source.getFileName().toString().equals("FinanceApplicationService.java")) {
                offenders.add(source.getFileName().toString());
            }
        }

        assertTrue(offenders.isEmpty(),
                "运费流水的 referenceType 字面量 \"LOGISTICS_ORDER\" 散落到别处了："
                        + String.join("、", offenders)
                        + " —— 幂等查询、入账与撤销都按这一个类型收敛，多处定义会各查不到对方");
        assertTrue(Files.readString(resolveDir(FINANCE_APPLICATION_SERVICE)).contains("\"LOGISTICS_ORDER\""),
                "定义处必须是 FinanceApplicationService：找不到字面量说明常量被挪走了，这条守卫会静默失效");
    }

    @Test
    @DisplayName("每条删物流单的路径都必须先撤销它的运费流水（ISS-139）")
    void everyLogisticsOrderDeletionReversesFreightTransactions() throws IOException {
        // 缺陷的形状是"删了单据、留了应付"，而删物流单有两条入口（DELETE /api/logistics/{id}
        // 与订单级联删除）。分母取自"哪几个文件会删物流单行"，新增第三条入口不接撤销线就会变红。
        List<String> deletingFiles = new ArrayList<>();
        for (Path source : javaSources(MAIN_SOURCE_DIR)) {
            String text = Files.readString(source);
            long deletions = countOccurrences(text, "logisticsOrderRepository.delete(");
            if (deletions == 0) {
                continue;
            }
            deletingFiles.add(source.getFileName().toString());
            long reversals = countOccurrences(text, "reverseTransactionsForDocument(");
            assertTrue(reversals >= deletions, source.getFileName() + " 删了 " + deletions
                    + " 处物流单却只撤销 " + reversals + " 处流水：被删单据的运费应付会变成指向空单据的活账");
        }

        assertEquals(2, deletingFiles.size(),
                "删物流单行的文件应为 2 个（物流应用服务 delete + 订单应用服务级联），实际 "
                        + String.join("、", deletingFiles)
                        + " —— 新增删除入口时要把这条守卫和撤销调用一起接上");
    }

    @Test
    @DisplayName("撤销口径：SUCCESS 分录拒绝随单据消失，PENDING 置为已取消留痕")
    void reversalKeepsAuditedEntriesAndCancelsPendingOnes() throws IOException {
        String source = Files.readString(resolveDir(FINANCE_APPLICATION_SERVICE));

        assertTrue(source.contains("TransactionStatus.SUCCESS"),
                "撤销必须拒绝已确认成功（SUCCESS）的分录：已过账的分录不能凭空消失，只能冲销");
        assertTrue(source.contains("TransactionStatus.PENDING"),
                "撤销必须把挂账中（PENDING）的分录置为 CANCELLED 留痕，而不是删掉流水行");
        assertTrue(!source.contains("deleteFinancialTransaction"),
                "财务侧不得提供删流水行的口：删分录等于销毁审计证据（ISS-139 的 R-59 口径）");
    }

    @Test
    @DisplayName("入账必须经财务应用服务，不得绕到财务仓库直写")
    void postingGoesThroughFinanceApplicationService() throws IOException {
        String source = Files.readString(resolveDir(LOGISTICS_APPLICATION_SERVICE));

        assertTrue(source.contains("site.hexaarch.ecommerce.logistics.application.service.finance.FinanceApplicationService"),
                "入账必须走 FinanceApplicationService：那里才有金额非零与租户归属的校验（ISS-118）");
        assertTrue(!source.contains("FinancialTransactionRepository"),
                "物流侧不得直连财务仓库写流水：绕开校验的入账会落进没人查得到的租户桶");
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
