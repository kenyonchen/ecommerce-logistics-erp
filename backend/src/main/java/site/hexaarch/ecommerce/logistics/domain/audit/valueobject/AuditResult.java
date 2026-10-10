package site.hexaarch.ecommerce.logistics.domain.audit.valueobject;

/**
 * 审计结果：被审计操作最终是成功还是失败。
 * <p>
 * 失败记录同样必须落库 —— 越权尝试与提权探测的信号主要在失败侧
 * （见 docs/tech/security-design.md §7 审计目标）。
 * </p>
 *
 * @author kenyon
 */
public enum AuditResult {
    SUCCESS,
    FAILURE
}
