package site.hexaarch.ecommerce.logistics.domain.product.valueobject;

/**
 * 字段取值转换规则，用于处理平台数据与系统标准单位之间的差异。
 *
 * @author kenyon
 */
public enum FieldTransform {
    /**
     * 不转换
     */
    NONE("不转换"),
    /**
     * 千克转克
     */
    WEIGHT_KG_TO_G("千克→克"),
    /**
     * 是/否（含 true/false、1/0）转布尔
     */
    BOOLEAN_FROM_YES_NO("是/否→布尔"),
    /**
     * 该字段缺失时使用默认值填充
     */
    DEFAULT_VALUE("缺失值填充默认值");

    private final String label;

    FieldTransform(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
