package site.hexaarch.ecommerce.logistics.domain.product.valueobject;

import lombok.Builder;
import lombok.Value;

/**
 * 产品属性值对象，描述产品的特性，如颜色、尺寸等。
 *
 * @author kenyon
 */
@Value
@Builder(toBuilder = true)
public class ProductAttribute {
    /**
     * 属性名称
     */
    String name;
    /**
     * 属性值
     */
    String value;
}
