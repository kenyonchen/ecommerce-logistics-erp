package site.hexaarch.ecommerce.logistics.domain.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要留审计日志的接口方法，由 {@code AuditAspect} 在环绕通知里落 audit_logs。
 * <p>
 * 口径来自 docs/tech/security-design.md §9 第 8 项：身份/权限/金额/凭据变更一律审计。
 * 用注解而不是在每个控制器里手写，是为了让"新增敏感端点"与"它是否被审计"在同一处可见。
 * </p>
 *
 * @author kenyon
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {

    /**
     * 机器可读动作码，建议使用 {@code 对象_动词} 大写蛇形，如 {@code USER_ROLE_ASSIGN}
     */
    String action();

    /**
     * 界面展示用的中文动作描述
     */
    String label();

    /**
     * 操作对象类型；留空时由切面用方法所在控制器名兜底
     */
    String targetType() default "";

    /**
     * 取目标 ID 的方法入参名；留空时切面优先找名为 {@code id} 的入参，再退到第一个字符串入参
     */
    String targetIdParam() default "";

    /**
     * 创建类端点的目标 ID 只在返回值里（入参只有请求体），这里声明返回体上的字段名，如 {@code channelId}。
     * <p>
     * 留空时切面按 {@code id} → {@code <targetType 小驼峰>Id} 的顺序试取；两者都取不到时留 NULL，
     * 绝不扫描任意 {@code *Id} getter——{@code OrderDto} 上同时有 {@code customerId} 与
     * {@code platformOrderId}，猜错会把留痕指向别的单据（ISS-129）。
     */
    String targetIdResult() default "";

    /**
     * 固定补充说明。禁止填写口令、令牌、平台凭据等敏感值
     */
    String detail() default "";
}
