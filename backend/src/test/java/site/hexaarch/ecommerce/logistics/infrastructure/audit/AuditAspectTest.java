package site.hexaarch.ecommerce.logistics.infrastructure.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.ResponseEntity;
import site.hexaarch.ecommerce.logistics.application.service.audit.AuditApplicationService;
import site.hexaarch.ecommerce.logistics.domain.audit.Auditable;
import site.hexaarch.ecommerce.logistics.domain.audit.entity.AuditLog;
import site.hexaarch.ecommerce.logistics.domain.audit.valueobject.AuditResult;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformConnectionTestResult;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;
import site.hexaarch.ecommerce.logistics.interfaces.dto.logistics.BatchShipResultDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.orderimport.OrderImportErrorDto;
import site.hexaarch.ecommerce.logistics.interfaces.dto.orderimport.OrderImportJobDto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审计切面测试（BR-12 / security-design §7）。
 * <p>
 * 盯住四件重构时容易悄悄坏掉的事：
 * <ol>
 *   <li>操作人必须在业务方法<b>执行前</b>取值——登出会在方法体内清空 SecurityContext，
 *       事后取到的是匿名，留痕就没有主体。</li>
 *   <li>失败必须留痕，且异常必须原样抛出——审计不得改变接口语义。</li>
 *   <li>目标 ID 只能取字符串型标识：绝不把请求体 toString 入库（可能含口令），
 *       也不得把 tenantId 冒充目标 ID。</li>
 *   <li>审计写库失败只能吞掉留痕，不能把业务请求打成 500。</li>
 *   <li>业务失败有四路：抛异常、{@code Result.code != 200}（可能被 {@code ResponseEntity} 包着）、
 *       批量端点与导入作业的行级未生效（{@code UnfinishedRowReport}），以及平台同步的
 *       {@code Map.failures}。四路都必须落成 FAILURE。</li>
 * </ol>
 *
 * @author kenyon
 */
class AuditAspectTest {

    private AuditApplicationService auditApplicationService;
    private AuditRequestContext auditRequestContext;
    private AuditAspect auditAspect;

    @BeforeEach
    void setUp() {
        auditApplicationService = mock(AuditApplicationService.class);
        auditRequestContext = mock(AuditRequestContext.class);
        auditAspect = new AuditAspect(auditApplicationService, auditRequestContext);

        when(auditRequestContext.actorName()).thenReturn("admin");
        when(auditRequestContext.actorId("admin")).thenReturn("user-1");
        when(auditRequestContext.tenantId()).thenReturn("tenant-a");
        when(auditRequestContext.clientIp()).thenReturn("10.0.0.8");
        when(auditRequestContext.traceId()).thenReturn("trace-1");
    }

    @Test
    @DisplayName("成功调用：落 SUCCESS 全字段留痕并原样返回业务结果")
    void recordsSuccessWithFullContext() throws Throwable {
        Auditable auditable = annotation("annotatedWithId");

        Object returned = auditAspect.audit(
                joinPoint(auditable, UserController.class, new String[]{"id"}, new Object[]{"user-42"}, "HTTP-OK"),
                auditable);

        assertEquals("HTTP-OK", returned);
        AuditLog entry = captured();
        assertEquals("USER_UPDATE", entry.getAction());
        assertEquals("更新用户资料", entry.getActionLabel());
        assertEquals(AuditResult.SUCCESS, entry.getResult());
        assertNull(entry.getErrorMessage());
        assertEquals("admin", entry.getActorName());
        assertEquals("user-1", entry.getActorId());
        assertEquals("tenant-a", entry.getTenantId());
        assertEquals("10.0.0.8", entry.getIp());
        assertEquals("trace-1", entry.getTraceId());
        assertEquals("user-42", entry.getTargetId());
        assertEquals("User", entry.getTargetType(), "注解显式 targetType 应直接采用");
    }

    @Test
    @DisplayName("业务方法抛异常：留 FAILURE 与异常摘要，且异常原样上抛")
    void recordsFailureAndRethrows() throws Throwable {
        Auditable auditable = annotation("annotatedWithId");
        ProceedingJoinPoint joinPoint = joinPoint(auditable, UserController.class,
                new String[]{"id"}, new Object[]{"user-42"}, null);
        BusinessException failure = new BusinessException("无权访问其他租户的数据", "TENANT_ACCESS_DENIED");
        when(joinPoint.proceed()).thenThrow(failure);

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> auditAspect.audit(joinPoint, auditable));

        assertSame(failure, thrown);
        AuditLog entry = captured();
        assertEquals(AuditResult.FAILURE, entry.getResult());
        assertEquals("BusinessException: 无权访问其他租户的数据", entry.getErrorMessage());
    }

    @Test
    @DisplayName("操作人/租户/IP/traceId 必须在业务方法执行之前取")
    void capturesActorBeforeProceed() throws Throwable {
        Auditable auditable = annotation("annotatedWithId");
        ProceedingJoinPoint joinPoint = joinPoint(auditable, UserController.class,
                new String[]{"id"}, new Object[]{"user-42"}, "ok");

        auditAspect.audit(joinPoint, auditable);

        InOrder order = inOrder(auditRequestContext, joinPoint);
        order.verify(auditRequestContext).actorName();
        order.verify(auditRequestContext).actorId("admin");
        order.verify(auditRequestContext).tenantId();
        order.verify(auditRequestContext).clientIp();
        order.verify(auditRequestContext).traceId();
        order.verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("目标 ID 优先级：注解指定入参 > 名为 id 的入参 > 第一个字符串入参")
    void resolvesTargetIdByPriority() throws Throwable {
        AuditLog explicit = runAndGet(annotation("annotatedWithExplicitParam"), UserController.class,
                new String[]{"tenantId", "roleId", "id"}, new Object[]{"tenant-a", "role-9", "user-42"});
        assertEquals("role-9", explicit.getTargetId(), "注解指定的 roleId 应优先于 id");

        AuditLog namedId = runAndGet(annotation("annotatedWithId"), UserController.class,
                new String[]{"tenantId", "id"}, new Object[]{"tenant-a", "user-42"});
        assertEquals("user-42", namedId.getTargetId(), "名为 id 的入参应优先于其他字符串入参");

        AuditLog fallback = runAndGet(annotation("annotatedWithId"), UserController.class,
                new String[]{"userId"}, new Object[]{"user-77"});
        assertEquals("user-77", fallback.getTargetId());
    }

    @Test
    @DisplayName("只有 tenantId 入参时不得拿它当目标 ID，请求体对象绝不入库")
    void neverUsesTenantIdOrRequestBodyAsTargetId() throws Throwable {
        Auditable auditable = annotation("annotatedWithId");

        AuditLog tenantOnly = runAndGet(auditable, UserController.class,
                new String[]{"tenantId"}, new Object[]{"tenant-a"});
        assertNull(tenantOnly.getTargetId(), "tenantId 已由 tenant_id 列承载，不能同时冒充目标 ID");

        AuditLog withBody = runAndGet(auditable, UserController.class,
                new String[]{"tenantId", "request"}, new Object[]{"tenant-a", new SecretBody()});
        assertNull(withBody.getTargetId(), "请求体 toString 可能含口令，禁止写进审计表");
    }

    @Test
    @DisplayName("targetType 留空时按控制器名去掉 Controller 兜底")
    void fallsBackToDeclaringControllerNameForTargetType() throws Throwable {
        AuditLog entry = runAndGet(annotation("annotatedWithoutTargetType"), OrderController.class,
                new String[]{"id"}, new Object[]{"o-1"});
        assertEquals("Order", entry.getTargetType());
    }

    @Test
    @DisplayName("审计写库失败只吞掉留痕，业务结果照常返回")
    void auditWriteFailureDoesNotBreakRequest() throws Throwable {
        doThrow(new RuntimeException("audit_logs 写入失败")).when(auditApplicationService).record(any());
        Auditable auditable = annotation("annotatedWithId");

        Object returned = auditAspect.audit(
                joinPoint(auditable, UserController.class, new String[]{"id"}, new Object[]{"user-42"}, "HTTP-OK"),
                auditable);

        assertEquals("HTTP-OK", returned, "留痕失败不得让业务请求变 500");
    }

    @Test
    @DisplayName("超长文本截断到 500；未声明 detail 时保持 null 而不是空串")
    void truncatesLongTextAndLeavesBlankDetailNull() throws Throwable {
        AuditLog truncated = runAndGet(annotation("annotatedWithId"), UserController.class,
                new String[]{"id"}, new Object[]{"x".repeat(900)});
        assertEquals(500, truncated.getTargetId().length());

        AuditLog noDetail = runAndGet(annotation("annotatedWithoutTargetType"), OrderController.class,
                new String[]{"id"}, new Object[]{"o-1"});
        assertNull(noDetail.getDetail());

        AuditLog withDetail = runAndGet(annotation("annotatedWithDetail"), OrderController.class,
                new String[]{"id"}, new Object[]{"o-1"});
        assertEquals("只改状态不改数据", withDetail.getDetail());
    }

    @Test
    @DisplayName("控制器把业务失败压成 HTTP 200 + code!=200 时，留痕必须是 FAILURE")
    void recordsFailureWhenResultCarriesBusinessError() throws Throwable {
        Auditable auditable = annotation("annotatedWithId");
        Result<String> rejected = Result.error("创建财务交易失败: 金额必须为正数");

        Object returned = auditAspect.audit(joinPoint(auditable, UserController.class,
                new String[]{"id"}, new Object[]{"tx-1"}, rejected), auditable);

        assertSame(rejected, returned, "切面不得改写接口语义：还是原来那份响应体");
        AuditLog entry = captured();
        assertEquals(AuditResult.FAILURE, entry.getResult(),
                "业务没改成却记 SUCCESS，审计就等于凭空多出一次金额变更");
        assertEquals("Result(code=500): 创建财务交易失败: 金额必须为正数", entry.getErrorMessage());
    }

    @Test
    @DisplayName("Result 正常成功时仍记 SUCCESS，不得误判成失败")
    void recordsSuccessWhenResultCodeIs200() throws Throwable {
        Auditable auditable = annotation("annotatedWithId");

        auditAspect.audit(joinPoint(auditable, UserController.class,
                new String[]{"id"}, new Object[]{"tx-1"}, Result.success("ok")), auditable);

        assertEquals(AuditResult.SUCCESS, captured().getResult());
        assertNull(captured().getErrorMessage());
    }

    /**
     * ISS-117 / R-36：批量发货的行级失败被 {@code recordFailure()} 吞在响应体里，
     * HTTP 与 {@code Result.code} 都是 200。响应契约不改（改 207 会打坏所有调用方），
     * 但留痕不得谎报成"这批都改成了"。
     */
    @Test
    @DisplayName("批量端点 code=200 却有行没落地时，留痕必须是 FAILURE 并带逐行统计")
    void recordsFailureWhenBatchResultHasUnappliedRows() throws Throwable {
        Auditable auditable = annotation("annotatedWithId");
        BatchShipResultDto batch = BatchShipResultDto.of(2);
        batch.recordSuccess("LO-1");
        batch.recordFailure(2, "ORD-88", "订单状态不允许发货");
        batch.buildMessage();

        auditAspect.audit(joinPoint(auditable, UserController.class,
                new String[]{"id"}, new Object[]{"batch-1"}, Result.success(batch)), auditable);

        AuditLog entry = captured();
        assertEquals(AuditResult.FAILURE, entry.getResult(),
                "有一行没发出去却记 SUCCESS，取证时会以为整批都生效了");
        assertEquals("批量结果部分未生效: total=2 success=1 failure=1 skipped=0; 首条 ORD-88 订单状态不允许发货",
                entry.getErrorMessage());
    }

    @Test
    @DisplayName("批量端点整批成功（含全部跳过以外的零未处理行）仍记 SUCCESS")
    void recordsSuccessWhenBatchResultIsFullyApplied() throws Throwable {
        Auditable auditable = annotation("annotatedWithId");
        BatchShipResultDto batch = BatchShipResultDto.of(2);
        batch.recordSuccess("LO-1");
        batch.recordSuccess("LO-2");
        batch.buildMessage();

        auditAspect.audit(joinPoint(auditable, UserController.class,
                new String[]{"id"}, new Object[]{"batch-2"}, Result.success(batch)), auditable);

        assertEquals(AuditResult.SUCCESS, captured().getResult());
        assertNull(captured().getErrorMessage());
    }

    /**
     * R-38：6 个导入作业端点与店铺仓库同步同样是 {@code HTTP 200 + code=200}，行级未生效埋在
     * {@code errors} / {@code failures} 里。留痕口径必须与批量发货一致，否则"10 行只落 3 行"
     * 的作业与整批落地的作业留下一模一样的 SUCCESS，本轮补的 42 个注解就没有取证价值。
     */
    @Test
    @DisplayName("导入作业有未落库行时，留痕必须是 FAILURE 并带行号与原因")
    void recordsFailureWhenImportJobHasUnfinishedRows() throws Throwable {
        Auditable auditable = annotation("annotatedWithId");
        OrderImportJobDto job = new OrderImportJobDto();
        job.setTotalCount(2);
        job.setSuccessCount(1);
        job.setFailureCount(1);
        job.setSkippedCount(0);
        OrderImportErrorDto error = new OrderImportErrorDto();
        error.setRowNumber(2);
        error.setMessage("收件人不能为空");
        job.setErrors(List.of(error));

        auditAspect.audit(joinPoint(auditable, OrderController.class,
                new String[]{"id"}, new Object[]{"job-1"}, Result.success(job)), auditable);

        AuditLog entry = captured();
        assertEquals(AuditResult.FAILURE, entry.getResult(),
                "只落地一行的作业记成 SUCCESS，审计就等于多报一次导入成功");
        assertEquals("批量结果部分未生效: total=2 success=1 failure=1 skipped=0; 首条 第2行 收件人不能为空",
                entry.getErrorMessage());
    }

    @Test
    @DisplayName("平台同步返回 Map 且 failures 非空时，留痕必须是 FAILURE")
    void recordsFailureWhenPlatformSyncMapReportsFailures() throws Throwable {
        Auditable auditable = annotation("annotatedWithId");
        Map<String, Object> sync = new LinkedHashMap<>();
        sync.put("totalFetched", 2);
        sync.put("createdCount", 1);
        sync.put("updatedCount", 0);
        sync.put("failureCount", 1);
        sync.put("failures", List.of("第2行缺少平台仓库ID，已跳过"));

        auditAspect.audit(joinPoint(auditable, OrderController.class,
                new String[]{"integrationId"}, new Object[]{"int-1"}, Result.success(sync)), auditable);

        AuditLog entry = captured();
        assertEquals(AuditResult.FAILURE, entry.getResult());
        assertEquals("同步结果部分未生效: fetched=2 created=1 updated=0 failure=1; 首条 第2行缺少平台仓库ID，已跳过",
                entry.getErrorMessage());
    }

    /**
     * ISS-130 / R-48：连通性测试失败是走 {@code data.success=false} 回来的（HTTP 与 code 都是 200），
     * 载荷实现 {@code OperationOutcomeReport} 后必须落成 FAILURE，并把原因写进 error_message。
     * 两种信封形状都要覆盖——真端点带 {@code ResponseEntity}，但同一契约也允许直接返回 {@code Result}。
     */
    @Test
    @DisplayName("载荷自报 success=false 时，留痕必须是 FAILURE 并带原因")
    void recordsFailureWhenOutcomeSelfReportsUnsuccessful() throws Throwable {
        Auditable auditable = annotation("annotatedWithId");
        PlatformConnectionTestResult probe = PlatformConnectionTestResult.failed(
                "OZON", 812, "无法连接目标地址（端口不通、服务未启动或网络不通），请检查店铺地址(storeUrl)");

        auditAspect.audit(joinPoint(auditable, UserController.class,
                new String[]{"id"}, new Object[]{"int-1"}, ResponseEntity.ok(Result.success(probe))), auditable);

        AuditLog entry = captured();
        assertEquals(AuditResult.FAILURE, entry.getResult());
        assertEquals("HTTP 200 结果自报未通过: 连通性测试未通过(adapter=OZON): "
                + "无法连接目标地址（端口不通、服务未启动或网络不通），请检查店铺地址(storeUrl)", entry.getErrorMessage());
    }

    @Test
    @DisplayName("探活通过（success=true）仍记 SUCCESS，不得把自报契约误判成失败")
    void recordsSuccessWhenOutcomeSelfReportsSuccess() throws Throwable {
        Auditable auditable = annotation("annotatedWithId");

        auditAspect.audit(joinPoint(auditable, UserController.class, new String[]{"id"}, new Object[]{"int-1"},
                Result.success(PlatformConnectionTestResult.ok("OZON", 240, "client_id 可用"))), auditable);

        AuditLog entry = captured();
        assertEquals(AuditResult.SUCCESS, entry.getResult());
        assertNull(entry.getErrorMessage());
    }

    /**
     * ISS-123：返回 {@code ResponseEntity<Result<?>>} 的留痕端点有 37 个（平台集成、租户、用户、角色、
     * 登出），它们把业务失败压成 4xx + {@code Result.error}。切面原先只认 {@code Result} 形状，
     * "平台同步失败"在审计库里就是一条 SUCCESS —— 谎报面比 M-08 那条路径更大。
     */
    @Test
    @DisplayName("ResponseEntity 包着 4xx + Result.error 时，留痕必须是 FAILURE")
    void recordsFailureWhenResponseEntityCarriesBusinessError() throws Throwable {
        Auditable auditable = annotation("annotatedWithId");
        ResponseEntity<Result<String>> rejected = ResponseEntity.badRequest()
                .body(Result.error("SYNC_FAILED", "平台同步失败: 凭据无效"));

        auditAspect.audit(joinPoint(auditable, UserController.class,
                new String[]{"id"}, new Object[]{"int-1"}, rejected), auditable);

        AuditLog entry = captured();
        assertEquals(AuditResult.FAILURE, entry.getResult(),
                "同步没成功却记 SUCCESS，审计里等于凭空多出一整条平台同步");
        assertEquals("HTTP 400 Result(code=SYNC_FAILED): 平台同步失败: 凭据无效", entry.getErrorMessage());
    }

    @Test
    @DisplayName("ResponseEntity 4xx 但响应体没有 Result 时同样不得记成功")
    void recordsFailureWhenResponseEntityStatusIsErrorWithoutBody() throws Throwable {
        Auditable auditable = annotation("annotatedWithId");

        auditAspect.audit(joinPoint(auditable, UserController.class,
                new String[]{"id"}, new Object[]{"int-2"}, ResponseEntity.notFound().build()), auditable);

        AuditLog entry = captured();
        assertEquals(AuditResult.FAILURE, entry.getResult());
        assertEquals("HTTP 404 业务失败（响应体未给出错误码）", entry.getErrorMessage());
    }

    @Test
    @DisplayName("ResponseEntity.ok(Result.success(...)) 仍记 SUCCESS，不得误判")
    void recordsSuccessWhenResponseEntityCarriesSuccess() throws Throwable {
        Auditable auditable = annotation("annotatedWithId");

        auditAspect.audit(joinPoint(auditable, UserController.class,
                new String[]{"id"}, new Object[]{"int-3"}, ResponseEntity.ok(Result.success("done"))), auditable);

        assertEquals(AuditResult.SUCCESS, captured().getResult());
        assertNull(captured().getErrorMessage());
    }

    // ------------------------------------------------------------------ 创建留痕的目标 ID

    /**
     * ISS-129：13 类 {@code *_CREATE} 动作共 658 条留痕的 target_id 全是 NULL —— 入参只有请求体，
     * 切面原先只扫入参。"这张单据是谁建的"于是无法按单据 id 检索，留痕等于只记了"有人建过东西"。
     */
    @Test
    @DisplayName("创建端点：入参没有 id 时从返回体取 id / <targetType>Id")
    void resolvesCreatedTargetIdFromResponseBody() throws Throwable {
        Auditable integration = annotation("annotatedCreateIntegration");
        auditAspect.audit(joinPoint(integration, PlatformIntegrationController.class,
                new String[]{"request"}, new Object[]{new SecretBody()},
                Result.success(new IntegrationLikeDto("b583afc9"))), integration);
        assertEquals("b583afc9", captured().getTargetId(), "返回体里的 id 就是新建记录的主键");

        Auditable order = annotation("annotatedCreateOrder");
        auditAspect.audit(joinPoint(order, OrderController.class,
                new String[]{"request"}, new Object[]{new SecretBody()},
                Result.success(new OrderLikeDto("o-9", "c-1", "EXT-1"))), order);
        assertEquals("o-9", captured().getTargetId(), "targetType=Order 应取 orderId");
    }

    @Test
    @DisplayName("创建端点：注解显式声明的 targetIdResult 优先于兜底规则")
    void prefersDeclaredResultFieldOverFallback() throws Throwable {
        Auditable channel = annotation("annotatedCreateChannel");

        auditAspect.audit(joinPoint(channel, OrderController.class,
                new String[]{"request"}, new Object[]{new SecretBody()},
                ResponseEntity.ok(Result.success(new ChannelLikeDto("ch-3")))), channel);

        assertEquals("ch-3", captured().getTargetId(), "LogisticsChannel 的主键叫 channelId，兜底规则拼不出来");
    }

    /**
     * 两条"不许猜"的负例：单据 DTO 上既有自己的 id 也有别人的外键，
     * 取错会让留痕指向别的单据，比留 NULL 更糟。
     */
    @Test
    @DisplayName("创建端点：取不到自己的主键时留 NULL，绝不退到别的外键或凭据字段")
    void leavesTargetIdNullInsteadOfGuessing() throws Throwable {
        Auditable unknown = annotation("annotatedCreateUnknown");
        auditAspect.audit(joinPoint(unknown, OrderController.class,
                new String[]{"request"}, new Object[]{new SecretBody()},
                Result.success(new OrderLikeDto(null, "c-1", "EXT-1"))), unknown);
        assertNull(captured().getTargetId(), "customerId / platformOrderId 是别家单据的 id，不能冒充目标");

        Auditable leaked = annotation("annotatedCreateWithSensitiveField");
        auditAspect.audit(joinPoint(leaked, OrderController.class,
                new String[]{"request"}, new Object[]{new SecretBody()},
                Result.success(new CredentialBearerDto("sk-plaintext-secret"))), leaked);
        assertNull(captured().getTargetId(), "注解写错成凭据字段时也不得把凭据落进审计表");
    }

    @Test
    @DisplayName("返回体是 record（裸访问器，没有 getX()）时同样能取到 id")
    void resolvesTargetIdFromRecordAccessor() throws Throwable {
        Auditable integration = annotation("annotatedCreateIntegration");

        auditAspect.audit(joinPoint(integration, PlatformIntegrationController.class,
                new String[]{"request"}, new Object[]{new SecretBody()},
                Result.success(new ShopLikeRecord("shop-7"))), integration);

        assertEquals("shop-7", captured().getTargetId());
    }

    // ------------------------------------------------------------------ 夹具

    /**
     * 无租户上下文场景（内部调用/事件线程）不应报错，只是留痕缺主体。
     */
    @Test
    @DisplayName("取不到操作人时仍落痕，字段留空而不是抛异常")
    void recordsEvenWithoutActor() throws Throwable {
        when(auditRequestContext.actorName()).thenReturn(null);
        when(auditRequestContext.clientIp()).thenReturn(null);
        Auditable auditable = annotation("annotatedWithId");

        auditAspect.audit(joinPoint(auditable, UserController.class,
                new String[]{"id"}, new Object[]{"user-42"}, "ok"), auditable);

        AuditLog entry = captured();
        assertNull(entry.getActorName());
        assertNull(entry.getIp());
        assertEquals(AuditResult.SUCCESS, entry.getResult());
    }

    /**
     * 注解取值基准：每个用例的 action/label/targetType 在这里写死一次。
     */
    @SuppressWarnings("unused")
    private static class Demo {
        @Auditable(action = "USER_UPDATE", label = "更新用户资料", targetType = "User")
        void annotatedWithId(String id) {
        }

        @Auditable(action = "USER_ROLE_ASSIGN", label = "为用户分配角色", targetType = "User", targetIdParam = "roleId")
        void annotatedWithExplicitParam(String tenantId, String roleId, String id) {
        }

        @Auditable(action = "ORDER_UPDATE", label = "更新订单")
        void annotatedWithoutTargetType(String id) {
        }

        @Auditable(action = "ORDER_STATUS_UPDATE", label = "更新订单状态", targetType = "Order", detail = "只改状态不改数据")
        void annotatedWithDetail(String id) {
        }

        @Auditable(action = "PLATFORM_INTEGRATION_CREATE", label = "创建平台集成")
        void annotatedCreateIntegration(@SuppressWarnings("unused") Object request) {
        }

        @Auditable(action = "ORDER_CREATE", label = "创建订单", targetType = "Order")
        void annotatedCreateOrder(@SuppressWarnings("unused") Object request) {
        }

        @Auditable(action = "LOGISTICS_CHANNEL_CREATE", label = "创建物流渠道",
                targetType = "LogisticsChannel", targetIdResult = "channelId")
        void annotatedCreateChannel(@SuppressWarnings("unused") Object request) {
        }

        @Auditable(action = "PURCHASE_CREATE", label = "新建采购单", targetType = "PurchaseOrder")
        void annotatedCreateUnknown(@SuppressWarnings("unused") Object request) {
        }

        @Auditable(action = "SHOP_CREATE", label = "新建店铺", targetType = "Shop", targetIdResult = "apiSecret")
        void annotatedCreateWithSensitiveField(@SuppressWarnings("unused") Object request) {
        }
    }

    /**
     * 创建端点的返回体形状：{@code id} 命名（平台集成/店铺/用户/角色/租户/商品）。
     */
    private static final class IntegrationLikeDto {
        private final String id;

        private IntegrationLikeDto(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    /**
     * 单据上同时挂着自家主键与别家外键：切面只能取 orderId。
     */
    private static final class OrderLikeDto {
        private final String orderId;
        private final String customerId;
        private final String platformOrderId;

        private OrderLikeDto(String orderId, String customerId, String platformOrderId) {
            this.orderId = orderId;
            this.customerId = customerId;
            this.platformOrderId = platformOrderId;
        }

        public String getOrderId() {
            return orderId;
        }

        public String getCustomerId() {
            return customerId;
        }

        public String getPlatformOrderId() {
            return platformOrderId;
        }
    }

    /**
     * 主键既不叫 {@code id} 也不能由 targetType 拼出来（LogisticsChannel → logisticsChannelId）。
     */
    private static final class ChannelLikeDto {
        private final String channelId;

        private ChannelLikeDto(String channelId) {
            this.channelId = channelId;
        }

        public String getChannelId() {
            return channelId;
        }
    }

    private static final class CredentialBearerDto {
        private final String apiSecret;

        private CredentialBearerDto(String apiSecret) {
            this.apiSecret = apiSecret;
        }

        public String getApiSecret() {
            return apiSecret;
        }
    }

    /**
     * record 形状的返回体：访问器叫 {@code id()}，没有 {@code getId()}。
     */
    private record ShopLikeRecord(String id) {
    }

    /**
     * 模拟带口令字段的请求体：切面若把它 toString 入库就会被这条测试抓到。
     */
    private static final class SecretBody {
        @Override
        public String toString() {
            return "CreateUserRequest(password=SuperSecret123)";
        }
    }

    private static Auditable annotation(String methodName) {
        return IntStream.range(0, Demo.class.getDeclaredMethods().length)
                .mapToObj(index -> Demo.class.getDeclaredMethods()[index])
                .filter(method -> method.getName().equals(methodName))
                .map(method -> method.getAnnotation(Auditable.class))
                .findFirst()
                .orElseThrow(() -> new AssertionError("夹具方法缺少 @Auditable: " + methodName));
    }

    private AuditLog runAndGet(Auditable auditable, Class<?> declaringType,
                               String[] parameterNames, Object[] args) throws Throwable {
        auditAspect.audit(joinPoint(auditable, declaringType, parameterNames, args, "returned"), auditable);
        return captured();
    }

    private ProceedingJoinPoint joinPoint(Auditable auditable, Class<?> declaringType,
                                          String[] parameterNames, Object[] args, Object proceedResult)
            throws Throwable {
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getParameterNames()).thenReturn(parameterNames);
        when(signature.getDeclaringType()).thenReturn(declaringType);
        when(signature.toShortString()).thenReturn(declaringType.getSimpleName() + ".method()");
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(args);
        if (proceedResult != null) {
            when(joinPoint.proceed()).thenReturn(proceedResult);
        }
        return joinPoint;
    }

    /**
     * 取最近一次写入的留痕（一个用例里会跑多次切面调用）。
     */
    private AuditLog captured() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditApplicationService, atLeastOnce()).record(captor.capture());
        return captor.getValue();
    }

    /**
     * 仅用于给 targetType 兜底提供「XxxController」的形状。
     */
    private static class UserController {
    }

    private static class OrderController {
    }

    private static class PlatformIntegrationController {
    }
}
