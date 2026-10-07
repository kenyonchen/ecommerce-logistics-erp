package site.hexaarch.ecommerce.logistics.domain.product.valueobject;

/**
 * 商品导入任务状态
 *
 * @author kenyon
 */
public enum ImportJobStatus {
    /**
     * 已创建，等待执行
     */
    PENDING("待执行"),
    /**
     * 执行中
     */
    RUNNING("执行中"),
    /**
     * 全部成功
     */
    SUCCESS("全部成功"),
    /**
     * 部分成功（存在失败行）
     */
    PARTIAL_SUCCESS("部分成功"),
    /**
     * 执行失败（如拉取平台数据失败）
     */
    FAILED("执行失败");

    private final String label;

    ImportJobStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
