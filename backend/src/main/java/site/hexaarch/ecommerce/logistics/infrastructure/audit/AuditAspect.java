package site.hexaarch.ecommerce.logistics.infrastructure.audit;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import site.hexaarch.ecommerce.logistics.application.service.audit.AuditApplicationService;
import site.hexaarch.ecommerce.logistics.domain.audit.Auditable;
import site.hexaarch.ecommerce.logistics.domain.audit.OperationOutcomeReport;
import site.hexaarch.ecommerce.logistics.domain.audit.entity.AuditLog;
import site.hexaarch.ecommerce.logistics.domain.audit.valueobject.AuditResult;
import site.hexaarch.ecommerce.logistics.interfaces.common.Result;
import site.hexaarch.ecommerce.logistics.interfaces.common.UnfinishedRowReport;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

/**
 * 敏感操作审计切面：把 {@link Auditable} 标注的接口调用落成 audit_logs 记录。
 * <p>
 * 成功与失败都要留痕，失败原因（异常类型 + 消息）截断后入库；异常照常向上抛，
 * 审计不改变接口语义。审计本身写库失败只打 WARN —— 不能因为留痕失败而让业务请求 500。
 * </p>
 * <p>
 * 切面挂在控制器方法上（在应用服务事务之外），所以失败记录不会被回滚的业务事务带走；
 * 应用服务侧仍用 {@code REQUIRES_NEW} 兜住"审计注解打到服务方法上"的情形。
 * </p>
 * <p>
 * 失败判定有五路：抛出异常；返回体是 {@code code != 200} 的 {@link Result}（含被
 * {@link ResponseEntity} 包着的情形）；批量 / 作业类响应里的行级未生效
 * （{@link UnfinishedRowReport}，HTTP 与 code 都是 200）；平台同步 {@code Map.failures} 非空；
 * 以及载荷自报未通过（{@link OperationOutcomeReport}，连通性测试的 {@code data.success=false}）。
 * 第二路是必须的——
 * 部分控制器把业务失败 try/catch 压成 HTTP 200 + code=500，只认异常会让审计凭空多出一次
 * 「金额已变更」的成功记录；第三路见 R-36 / R-38，第五路见 R-48 / ISS-130。
 * </p>
 *
 * @author kenyon
 */
@Slf4j
@Aspect
@Component
public class AuditAspect {

    private final AuditApplicationService auditApplicationService;
    private final AuditRequestContext auditRequestContext;

    public AuditAspect(AuditApplicationService auditApplicationService, AuditRequestContext auditRequestContext) {
        this.auditApplicationService = auditApplicationService;
        this.auditRequestContext = auditRequestContext;
    }

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        // 操作人/IP/traceId 必须在方法执行前取：登出这类端点会在方法体内清空 SecurityContext，
        // 事后取到的是匿名，留痕就没有主体了
        String actorName = auditRequestContext.actorName();
        String actorId = auditRequestContext.actorId(actorName);
        String tenantId = auditRequestContext.tenantId();
        String ip = auditRequestContext.clientIp();
        String traceId = auditRequestContext.traceId();

        Throwable failure = null;
        Object returned = null;
        try {
            returned = joinPoint.proceed();
            return returned;
        } catch (Throwable throwable) {
            failure = throwable;
            throw throwable;
        } finally {
            writeAudit(joinPoint, auditable, failure, returned, actorName, actorId, tenantId, ip, traceId);
        }
    }

    private void writeAudit(ProceedingJoinPoint joinPoint, Auditable auditable, Throwable failure, Object returned,
                            String actorName, String actorId, String tenantId, String ip, String traceId) {
        try {
            String businessFailure = describeBusinessFailure(returned);
            String targetType = resolveTargetType(joinPoint, auditable);
            String targetId = resolveTargetId(joinPoint, auditable);
            if (targetId == null) {
                targetId = resolveCreatedId(returned, auditable, targetType);
            }
            AuditLog entry = AuditLog.builder()
                    .tenantId(tenantId)
                    .actorId(actorId)
                    .actorName(actorName)
                    .action(auditable.action())
                    .actionLabel(auditable.label())
                    .targetType(targetType)
                    .targetId(targetId)
                    .detail(blankToNull(auditable.detail()))
                    .result(failure != null || businessFailure != null ? AuditResult.FAILURE : AuditResult.SUCCESS)
                    .errorMessage(AuditRequestContext.truncate(
                            failure != null ? describe(failure) : businessFailure))
                    .ip(ip)
                    .traceId(traceId)
                    .build();
            auditApplicationService.record(entry);
        } catch (RuntimeException ex) {
            log.warn("[Audit] 审计写入失败: action={} method={}", auditable.action(),
                    joinPoint.getSignature().toShortString(), ex);
        }
    }

    private String resolveTargetType(ProceedingJoinPoint joinPoint, Auditable auditable) {
        if (!auditable.targetType().isBlank()) {
            return auditable.targetType();
        }
        String declaringName = joinPoint.getSignature().getDeclaringType().getSimpleName();
        return declaringName.endsWith("Controller")
                ? declaringName.substring(0, declaringName.length() - "Controller".length())
                : declaringName;
    }

    /**
     * 目标 ID 取值优先级：注解显式指定的入参名 → 名为 {@code id} 的入参 → 第一个可转字符串的入参。
     * <p>
     * 只在 String / UUID 里取，避免把请求体对象整个 toString 写进审计表（可能带口令等敏感字段）。
     * </p>
     */
    private String resolveTargetId(ProceedingJoinPoint joinPoint, Auditable auditable) {
        if (!(joinPoint.getSignature() instanceof MethodSignature signature)) {
            return null;
        }
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        if (parameterNames == null) {
            return null;
        }
        String wanted = auditable.targetIdParam();
        String fallback = null;
        String namedId = null;
        for (int i = 0; i < parameterNames.length && i < args.length; i++) {
            // tenantId 不参与兜底：租户已经记在 tenant_id 列上，把它当目标 ID 会让检索结果自相矛盾
            if ("tenantId".equals(parameterNames[i]) && !parameterNames[i].equals(wanted)) {
                continue;
            }
            String value = asIdText(args[i]);
            if (value == null) {
                continue;
            }
            if (parameterNames[i].equals(wanted)) {
                return value;
            }
            if ("id".equals(parameterNames[i]) && namedId == null) {
                namedId = value;
            }
            if (fallback == null) {
                fallback = value;
            }
        }
        return namedId != null ? namedId : fallback;
    }

    private String asIdText(Object arg) {
        if (arg instanceof String text) {
            return text.isBlank() ? null : AuditRequestContext.truncate(text);
        }
        if (arg instanceof java.util.UUID uuid) {
            return uuid.toString();
        }
        return null;
    }

    /**
     * 创建类端点的目标 ID 只存在于返回值里（入参只有请求体），入参取不到时从返回体取（ISS-129）。
     * <p>
     * 取值顺序：注解显式声明的 {@link Auditable#targetIdResult()} → {@code id} →
     * {@code <targetType 小驼峰>Id}。三者都取不到就留 NULL —— 不扫描任意 {@code *Id} getter，
     * {@code OrderDto} 上同时有 {@code customerId} 与 {@code platformOrderId}，猜错会把留痕指向别的单据。
     * </p>
     */
    private String resolveCreatedId(Object returned, Auditable auditable, String targetType) {
        Object data = unwrapResultData(returned);
        if (data == null) {
            return null;
        }
        String declared = auditable.targetIdResult();
        if (!declared.isBlank()) {
            return readIdProperty(data, declared);
        }
        String ownId = readIdProperty(data, "id");
        return ownId != null ? ownId : readIdProperty(data, decapitalize(targetType) + "Id");
    }

    /**
     * 拆到返回体里的业务对象：{@code ResponseEntity} 与 {@code Result} 都只是信封。
     */
    private Object unwrapResultData(Object returned) {
        if (returned instanceof ResponseEntity<?> response) {
            return unwrapResultData(response.getBody());
        }
        if (returned instanceof Result<?> result) {
            return unwrapResultData(result.getData());
        }
        return returned;
    }

    /**
     * 按字段名读标识：先试 JavaBean getter，再试 record 的裸访问器；只认 String / UUID。
     * <p>
     * 名称里带凭据语义的一律拒绝（{@code password}/{@code secret}/{@code token}/{@code key}/
     * {@code credential}）——注解写错时也不把凭据喂进审计表。取不到就返回 null，不抛异常。
     * </p>
     */
    private String readIdProperty(Object target, String propertyName) {
        if (propertyName == null || propertyName.isBlank() || isSensitiveProperty(propertyName)) {
            return null;
        }
        String getter = "get" + capitalize(propertyName);
        try {
            Object value = invokeAccessor(target, getter);
            if (value == null) {
                value = invokeAccessor(target, propertyName);
            }
            if (value instanceof String text) {
                return text.isBlank() ? null : AuditRequestContext.truncate(text);
            }
            if (value instanceof java.util.UUID uuid) {
                return uuid.toString();
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            log.debug("[Audit] 从返回体取目标 ID 失败: type={} property={}",
                    target.getClass().getSimpleName(), propertyName);
        }
        return null;
    }

    private Object invokeAccessor(Object target, String methodName) throws ReflectiveOperationException {
        Method method;
        try {
            method = target.getClass().getMethod(methodName);
        } catch (NoSuchMethodException absent) {
            // 不是 JavaBean 形状（record 的裸访问器）时由调用方按字段名再试一次
            return null;
        }
        if (method.getParameterCount() > 0 || method.getReturnType() == void.class) {
            return null;
        }
        return method.invoke(target);
    }

    private boolean isSensitiveProperty(String propertyName) {
        String lower = propertyName.toLowerCase();
        return lower.contains("password") || lower.contains("secret") || lower.contains("token")
                || lower.contains("key") || lower.contains("credential");
    }

    private String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private String decapitalize(String text) {
        return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

    /**
     * 注解没写 detail 时落 NULL 而不是空串：检索「有没有补充说明」时空串与缺失不是一回事。
     */
    private String blankToNull(String text) {
        return text == null || text.isBlank() ? null : AuditRequestContext.truncate(text);
    }

    private String describe(Throwable failure) {
        return failure.getClass().getSimpleName()
                + (failure.getMessage() == null ? "" : ": " + failure.getMessage());
    }

    /**
     * 返回体里的业务失败：{@code Result.code} 不是 200 就是没改成，成功留痕会变成谎报。
     * <p>
     * {@code ResponseEntity} 必须先拆包再看 body：平台集成/租户/用户/角色/登出共 37 个留痕端点
     * 用这个形状把业务失败压成 4xx + {@code Result.error}，只认 {@code Result} 会让
     * "同步失败"在审计库里留下一条 SUCCESS（ISS-123）。
     * </p>
     *
     * @return 失败摘要；返回成功或非 {@code Result} 时给出 null
     */
    private String describeBusinessFailure(Object returned) {
        if (returned instanceof ResponseEntity<?> response) {
            String bodyFailure = describeBusinessFailure(response.getBody());
            if (bodyFailure != null) {
                return "HTTP " + response.getStatusCode().value() + " " + bodyFailure;
            }
            return response.getStatusCode().isError()
                    ? "HTTP " + response.getStatusCode().value() + " 业务失败（响应体未给出错误码）"
                    : null;
        }
        if (!(returned instanceof Result<?> result)) {
            return null;
        }
        String code = result.getCode();
        if (code != null && !"200".equals(code)) {
            return "Result(code=" + code + "): " + (result.getMessage() == null ? "业务处理失败" : result.getMessage());
        }
        if (result.getData() instanceof OperationOutcomeReport report) {
            String reason = report.failureReason();
            return reason == null ? null : "结果自报未通过: " + reason;
        }
        return describePartialBatch(result.getData());
    }

    /**
     * 批量 / 作业类端点的行级失败：响应是 {@code HTTP 200 + code=200}，但 {@code failures} 或
     * {@code errors} 里躺着没处理的行（ISS-117 / R-36）。响应契约不动 —— 换成 207/206 会同时
     * 打坏所有调用方和前端批量弹窗的判定；取证这一侧按"没全改成"记 FAILURE，并把逐行统计与
     * 第一条原因写进 error_message。
     * <p>
     * 摘要格式由 {@link UnfinishedRowReport} 集中定义，批量发货与 6 个导入作业端点共用一套口径
     * （R-38）。平台同步类端点把结果装成 {@code Map}（前端契约已固定，不便改成 DTO），
     * 这里按约定的 {@code failures} / {@code failureCount} 键判同一件事。
     * </p>
     */
    private String describePartialBatch(Object data) {
        if (data instanceof UnfinishedRowReport report) {
            return report.unfinishedRows();
        }
        if (data instanceof Map<?, ?> map && map.get("failures") instanceof Collection<?> failures
                && !failures.isEmpty()) {
            return "同步结果部分未生效: fetched=" + map.get("totalFetched")
                    + " created=" + map.get("createdCount")
                    + " updated=" + map.get("updatedCount")
                    + " failure=" + (map.get("failureCount") != null ? map.get("failureCount") : failures.size())
                    + "; 首条 " + failures.iterator().next();
        }
        return null;
    }
}
