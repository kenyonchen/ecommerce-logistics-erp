package site.hexaarch.ecommerce.logistics.interfaces.common;

/**
 * 批量 / 作业类响应的「行级未生效」契约，供审计判定一次写操作到底有没有生效。
 * <p>
 * 这类端点的响应是 {@code HTTP 200 + Result.code=200}，行级失败被 {@code recordFailure()}
 * 或 {@code errors} 列表吞在响应体里（ISS-117 / R-36）。只看状态码会让"一批 10 行只落 3 行"
 * 与"全部落地"留下同一条 SUCCESS 留痕，取证时无法区分。响应契约本身不改（换 207 会同时打坏
 * 所有调用方与前端批量弹窗的判定），改由实现本接口的 DTO 自己报告未生效行。
 * </p>
 * <p>
 * 摘要文本的格式在这里集中一处：审计 {@code error_message} 的口径若散落在七个 DTO 里，
 * 检索一条缺陷就要记七种格式（与 {@code AuditActions} 把动作码收在一处同理）。
 * </p>
 *
 * @author kenyon
 */
public interface UnfinishedRowReport {

    /**
     * @return 未生效行的摘要文本；整批都落到位时返回 {@code null}（审计据此才允许记 SUCCESS）
     */
    String unfinishedRows();

    /**
     * 按四个计数拼出摘要，零未生效行时返回 null。
     *
     * @param firstDetail 首条未生效原因（可为空），形如 {@code ORD-88 订单状态不允许发货}
     */
    static String summarize(Integer totalCount, Integer successCount, Integer failureCount,
                            Integer skippedCount, String firstDetail) {
        int failure = failureCount == null ? 0 : failureCount;
        int skipped = skippedCount == null ? 0 : skippedCount;
        if (failure + skipped == 0) {
            return null;
        }
        String summary = "批量结果部分未生效: total=" + (totalCount == null ? 0 : totalCount)
                + " success=" + (successCount == null ? 0 : successCount)
                + " failure=" + failure + " skipped=" + skipped;
        return firstDetail == null || firstDetail.isBlank() ? summary : summary + "; 首条 " + firstDetail;
    }

    /**
     * 导入作业的行级错误摘要：只带行号与原因，不带行数据本身（行里可能有客户邮箱与地址）。
     */
    static String firstRowDetail(Integer rowNumber, String message) {
        String row = rowNumber == null ? "" : "第" + rowNumber + "行 ";
        return row + (message == null ? "该行导入失败" : message);
    }
}
