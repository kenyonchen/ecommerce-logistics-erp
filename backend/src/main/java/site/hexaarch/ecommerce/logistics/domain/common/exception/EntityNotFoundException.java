package site.hexaarch.ecommerce.logistics.domain.common.exception;

/**
 * 实体未找到异常，用于表示尝试操作不存在的实体时抛出的异常
 *
 * @author kenyon
 */
public class EntityNotFoundException extends BusinessException {
    public EntityNotFoundException(String entityType, String id) {
        super(entityType + " not found with id: " + id, "ENTITY_NOT_FOUND");
    }

    public EntityNotFoundException(String entityType, String id, Throwable cause) {
        super(entityType + " not found with id: " + id, "ENTITY_NOT_FOUND", cause);
    }
}
