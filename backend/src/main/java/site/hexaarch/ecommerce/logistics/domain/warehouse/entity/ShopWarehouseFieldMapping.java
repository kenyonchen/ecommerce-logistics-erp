package site.hexaarch.ecommerce.logistics.domain.warehouse.entity;

import site.hexaarch.ecommerce.logistics.domain.product.valueobject.FieldTransform;
import site.hexaarch.ecommerce.logistics.domain.warehouse.valueobject.ShopWarehouseImportTargetField;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 第三方平台字段到标准店铺仓库字段的映射关系。
 *
 * @author kenyon
 */
public class ShopWarehouseFieldMapping {
    private String id;
    private String tenantId;
    /**
     * 平台名称（PlatformName 的名称字符串，或 FILE 表示文件上传导入）
     */
    private String platform;
    private String sourceField;
    private ShopWarehouseImportTargetField targetField;
    private FieldTransform transform;
    private String defaultValue;
    private boolean enabled;
    private LocalDateTime createdAt;

    public static ShopWarehouseFieldMapping create(String tenantId, String platform, String sourceField,
                                                   ShopWarehouseImportTargetField targetField,
                                                   FieldTransform transform, String defaultValue) {
        ShopWarehouseFieldMapping mapping = new ShopWarehouseFieldMapping();
        mapping.id = UUID.randomUUID().toString();
        mapping.tenantId = tenantId;
        mapping.platform = platform;
        mapping.sourceField = sourceField;
        mapping.targetField = targetField;
        mapping.transform = transform == null ? FieldTransform.NONE : transform;
        mapping.defaultValue = defaultValue;
        mapping.enabled = true;
        mapping.createdAt = LocalDateTime.now();
        return mapping;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getSourceField() {
        return sourceField;
    }

    public void setSourceField(String sourceField) {
        this.sourceField = sourceField;
    }

    public ShopWarehouseImportTargetField getTargetField() {
        return targetField;
    }

    public void setTargetField(ShopWarehouseImportTargetField targetField) {
        this.targetField = targetField;
    }

    public FieldTransform getTransform() {
        return transform;
    }

    public void setTransform(FieldTransform transform) {
        this.transform = transform;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
