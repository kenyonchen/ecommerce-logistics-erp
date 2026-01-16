package site.hexaarch.ecommerce.logistics.domain.order.valueobject;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.Objects;

/**
 * 收货地址值对象，描述收货位置。
 *
 * @author kenyon
 */
@Getter
@Builder(toBuilder = true)
@Schema(description = "收货地址")
public class ShippingAddress {
    @Schema(description = "国家", requiredMode = Schema.RequiredMode.REQUIRED, example = "中国")
    private final String country;
    @Schema(description = "省", requiredMode = Schema.RequiredMode.REQUIRED, example = "XX省")
    private final String province;
    @Schema(description = "市", requiredMode = Schema.RequiredMode.REQUIRED, example = "XX市")
    private final String city;
    @Schema(description = "街道", requiredMode = Schema.RequiredMode.REQUIRED, example = "XX区XX路XX号")
    private final String street;
    @Schema(description = "邮编", requiredMode = Schema.RequiredMode.REQUIRED, example = "518000")
    private final String zipCode;
    @Schema(description = "收件人", requiredMode = Schema.RequiredMode.REQUIRED, example = "张三")
    private final String recipient;
    @Schema(description = "电话", requiredMode = Schema.RequiredMode.REQUIRED, example = "13800138000")
    private final String phone;

    /**
     * 构造函数，确保所有必填字段都不为空。
     *
     * @param country   国家
     * @param province  省份
     * @param city      城市
     * @param street    街道
     * @param zipCode   邮政编码
     * @param recipient 收件人
     * @param phone     电话
     */
    private ShippingAddress(String country, String province, String city, String street, String zipCode, String recipient, String phone) {
        this.country = Objects.requireNonNull(country, "Country cannot be null");
        this.province = Objects.requireNonNull(province, "Province cannot be null");
        this.city = Objects.requireNonNull(city, "City cannot be null");
        this.street = Objects.requireNonNull(street, "Street cannot be null");
        this.zipCode = Objects.requireNonNull(zipCode, "Zip code cannot be null");
        this.recipient = Objects.requireNonNull(recipient, "Recipient cannot be null");
        this.phone = Objects.requireNonNull(phone, "Phone cannot be null");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShippingAddress that = (ShippingAddress) o;
        return Objects.equals(country, that.country) &&
                Objects.equals(province, that.province) &&
                Objects.equals(city, that.city) &&
                Objects.equals(street, that.street) &&
                Objects.equals(zipCode, that.zipCode) &&
                Objects.equals(recipient, that.recipient) &&
                Objects.equals(phone, that.phone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(country, province, city, street, zipCode, recipient, phone);
    }
}