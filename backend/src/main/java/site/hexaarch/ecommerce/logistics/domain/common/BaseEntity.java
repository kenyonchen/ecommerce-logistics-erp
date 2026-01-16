package site.hexaarch.ecommerce.logistics.domain.common;

/**
 * 实体基类
 *
 * @author kenyon
 */
public abstract class BaseEntity {
    protected String id;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}