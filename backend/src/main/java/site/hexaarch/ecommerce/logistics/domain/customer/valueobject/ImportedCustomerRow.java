package site.hexaarch.ecommerce.logistics.domain.customer.valueobject;

/**
 * 标准化后的平台买家行（导入中间模型）。
 *
 * @param rowNumber       在原始数据中的行号（从1开始）
 * @param externalBuyerId 平台买家ID（幂等去重键）
 * @param customerName    客户名称（必填）
 * @param email           客户邮箱
 * @param phone           客户电话
 * @param company         公司
 * @param country         国家
 * @param province        省/州
 * @param city            城市
 * @param address         详细地址
 * @author kenyon
 */
public record ImportedCustomerRow(int rowNumber,
                                  String externalBuyerId,
                                  String customerName,
                                  String email,
                                  String phone,
                                  String company,
                                  String country,
                                  String province,
                                  String city,
                                  String address) {

    /**
     * 完整地址：国家/省州/城市/详细地址按非空部分以空格拼接，
     * 供客户档案落库时合并为单一 address 字段使用（客户模块不拆分存储地址结构）
     */
    public String fullAddress() {
        StringBuilder builder = new StringBuilder();
        for (String part : new String[]{country, province, city, address}) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(part.trim());
        }
        return builder.isEmpty() ? null : builder.toString();
    }
}
