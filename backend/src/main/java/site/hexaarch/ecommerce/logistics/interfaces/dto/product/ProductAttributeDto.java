package site.hexaarch.ecommerce.logistics.interfaces.dto.product;

/**
 * 产品属性DTO，描述SKU的变体属性（颜色、容量等）。
 *
 * @author kenyon
 */
public class ProductAttributeDto {
    private String name;
    private String value;

    public ProductAttributeDto() {
    }

    public ProductAttributeDto(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
