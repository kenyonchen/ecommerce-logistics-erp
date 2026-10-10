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
 * 删仓库的引用守卫（ISS-142）。
 * <p>
 * 缺陷的形状是"删父行不查子行"：仓库删除只守卫库存，采购单的 {@code warehouse_id} 与店铺仓库绑定的
 * {@code local_warehouse_id} 都是没有外键的普通 varchar 列，于是 API 删仓把引用掏成断链，
 * 实测 533 行采购单里 530 行指向已不存在的仓库，而采购域没有删除端点可以修。
 * </p>
 * <p>
 * 这类守卫最容易复发的方式是<b>后来又开一个删仓入口</b>（批量清理、导入回滚、租户级联）却只抄了库存那道门。
 * 所以这里的分母取自"哪几个文件会真删仓库行"，新增入口不接引用守卫就会变红 ——
 * 与 {@code FreightPostingGuardTest} 的"每处删物流单都撤销分录"是同一条思路（R-44）。
 * </p>
 *
 * @author kenyon
 */
class WarehouseReferenceGuardTest {

    private static final String MAIN_SOURCE_DIR = "src/main/java/site/hexaarch/ecommerce/logistics";

    private static final String WAREHOUSE_APPLICATION_SERVICE =
            MAIN_SOURCE_DIR + "/application/service/warehouse/WarehouseApplicationService.java";

    private static final String PURCHASE_ORDER_REPOSITORY =
            MAIN_SOURCE_DIR + "/domain/purchase/repository/PurchaseOrderRepository.java";

    private static final String PURCHASE_ORDER_REPOSITORY_IMPL =
            MAIN_SOURCE_DIR + "/infrastructure/persistence/repository/purchase/PurchaseOrderRepositoryImpl.java";

    private static final String PURCHASE_ORDER_JPA_ENTITY =
            MAIN_SOURCE_DIR + "/infrastructure/persistence/jpa/entity/purchase/PurchaseOrderJpaEntity.java";

    private static final String WAREHOUSE_CONTROLLER =
            MAIN_SOURCE_DIR + "/interfaces/controller/warehouse/WarehouseController.java";

    /**
     * 会真删仓库行的文件数：目前只有仓库应用服务一处。
     */
    private static final int EXPECTED_DELETING_FILES = 1;

    @Test
    @DisplayName("每个删仓库文件都必须先查两类引用，删除调用数不得超过守卫数")
    void everyWarehouseDeletionChecksReferences() throws IOException {
        List<String> deletingFiles = new ArrayList<>();
        for (Path source : javaSources(MAIN_SOURCE_DIR)) {
            String text = Files.readString(source);
            long deletions = countOccurrences(text, "warehouseRepository.delete(");
            if (deletions == 0) {
                continue;
            }
            deletingFiles.add(source.getFileName().toString());
            long purchaseChecks = countOccurrences(text, "purchaseOrderRepository.countByWarehouseId(");
            long bindingChecks = countOccurrences(text, "shopWarehouseRepository.findByLocalWarehouseId(");
            assertTrue(purchaseChecks >= deletions, source.getFileName() + " 删了 " + deletions
                    + " 处仓库却只查 " + purchaseChecks + " 次采购单引用：断链采购单查不到收货仓，"
                    + "而采购域没有删除端点可以修（ISS-142）");
            assertTrue(bindingChecks >= 1, source.getFileName() + " 删仓库却没查店铺仓库绑定："
                    + "绑定关系会变成指向空仓的店铺仓（shop_warehouses.local_warehouse_id）");
        }

        assertEquals(EXPECTED_DELETING_FILES, deletingFiles.size(),
                "删仓库行的文件应为 " + EXPECTED_DELETING_FILES + " 个（仓库应用服务），实际 "
                        + String.join("、", deletingFiles)
                        + " —— 新增删仓入口时必须把引用守卫一起接上，并把这条名册计数抬上去");
    }

    @Test
    @DisplayName("三道门都必须在删除动作之前：库存 → 采购单引用 → 店铺绑定")
    void referenceChecksHappenBeforeTheDelete() throws IOException {
        String source = Files.readString(resolveDir(WAREHOUSE_APPLICATION_SERVICE));
        int start = source.indexOf("public void deleteWarehouse(String warehouseId)");
        assertTrue(start >= 0, "找不到 deleteWarehouse，签名变了要同步改这条守卫");
        int end = source.indexOf("\n    }\n", start);
        String body = source.substring(start, end);

        int deleteAt = body.indexOf("warehouseRepository.delete(");
        int stockAt = body.indexOf("仍存在库存记录");
        int purchaseAt = body.indexOf("countByWarehouseId(");
        int bindingAt = body.indexOf("findByLocalWarehouseId(");

        assertTrue(deleteAt >= 0 && stockAt >= 0 && purchaseAt >= 0 && bindingAt >= 0,
                "库存门、采购单引用门、店铺绑定门与删除调用必须都在 deleteWarehouse 方法体内，实际位置 "
                        + stockAt + "/" + purchaseAt + "/" + bindingAt + "/" + deleteAt);
        assertTrue(stockAt < deleteAt && purchaseAt < deleteAt && bindingAt < deleteAt,
                "任何一道门排在 warehouseRepository.delete( 之后都等于没挡：删除已经发生了");
        assertTrue(purchaseAt < bindingAt,
                "采购单引用要在店铺绑定之前判：两者同时存在时，先报"
                        + "「有多少张单压着」对操作方更有用");
    }

    @Test
    @DisplayName("拒绝必须是可读中文原因的业务异常，不得退化成 500")
    void rejectionsAreReadableBusinessExceptions() throws IOException {
        String source = Files.readString(resolveDir(WAREHOUSE_APPLICATION_SERVICE));
        int start = source.indexOf("public void deleteWarehouse(String warehouseId)");
        String body = source.substring(start, source.indexOf("\n    }\n", start));

        assertTrue(body.contains("throw new ValidationException(\"仓库已被 \" + referencingOrders + \" 张采购单引用"),
                "采购单引用的拒绝原因必须点名单据类型并给出数量（ISS-142 的实测后果是断链 530 行，"
                        + "操作方需要知道有几张单压着）");
        assertTrue(body.contains("个店铺仓库") && body.contains("请先解除绑定"),
                "店铺绑定的拒绝原因必须说明怎么解开：只回「无法删除」等于把问题丢回给用户");
        assertTrue(!body.contains("IllegalArgumentException"),
                "业务拒绝抛 IllegalArgumentException 会被控制台的 catch (Exception) 复述成服务端故障"
                        + "（ISS-140 刚记过的反模式，这里不能重犯）");
    }

    @Test
    @DisplayName("引用计数走的是采购单表的 warehouse_id 列，且没有被租户过滤砍掉一半")
    void referenceCountReadsTheWarehouseColumn() throws IOException {
        String interfaceSource = Files.readString(resolveDir(PURCHASE_ORDER_REPOSITORY));
        assertTrue(interfaceSource.contains("long countByWarehouseId(String warehouseId);"),
                "域接口必须声明 countByWarehouseId：守卫只有绕开仓储这一层才能统计到引用，"
                        + "声明在接口上才不会哪天被换成内存过滤");

        String implSource = Files.readString(resolveDir(PURCHASE_ORDER_REPOSITORY_IMPL));
        int start = implSource.indexOf("public long countByWarehouseId(String warehouseId)");
        assertTrue(start >= 0, "仓储实现里找不到 countByWarehouseId，守卫会静默变成恒 0");
        String body = implSource.substring(start, implSource.indexOf("\n    }", start));
        assertTrue(body.contains("purchaseOrderJpaRepository.countByWarehouseId("),
                "计数必须下推到数据库：把整表捞进内存再 filter 等于把删仓守卫写成一次全表扫描");
        assertTrue(!body.contains("TenantContext"),
                "这条计数刻意不按租户过滤（仓ID是 UUID，多租户各算一遍会漏掉别家的断链引用），"
                        + "若在实现里读会话租户就把口径改窄了，要连同接口 javadoc 一起重议");

        String entitySource = Files.readString(resolveDir(PURCHASE_ORDER_JPA_ENTITY));
        assertTrue(entitySource.contains("@Column(name = \"warehouse_id\", nullable = false)"),
                "派生查询 countByWarehouseId 依赖 warehouse_id 列；列被改名或放开非空约束时这条守卫会失效，"
                        + "所以把它钉在这里");
    }

    @Test
    @DisplayName("删除仓库端点仍带写权限与审计留痕")
    void deleteEndpointKeepsAuthorizationAndAudit() throws IOException {
        String source = Files.readString(resolveDir(WAREHOUSE_CONTROLLER));
        int at = source.indexOf("@DeleteMapping(\"/{id}\")");
        assertTrue(at >= 0, "找不到 DELETE /api/warehouses/{id}");
        String head = source.substring(Math.max(0, at - 400), at);

        assertTrue(head.contains("@PreAuthorize(HAS_WAREHOUSE_WRITE)"),
                "删仓是破坏性写操作，权限点不能掉（ISS-112 的接线面）");
        assertTrue(head.contains("AuditActions.WAREHOUSE_DELETE"),
                "删仓必须留审计痕：引用守卫挡住之后，操作记录是唯一能追到「谁试图删过这张仓」的地方（ISS-114）");
        assertTrue(head.contains("被采购单引用或已绑定店铺仓库时禁止删除"),
                "接口描述要写出三道门：只写「存在库存时禁止删除」是对契约的错误承诺（ISS-089 同一形状）");
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
