package site.hexaarch.ecommerce.logistics.domain.platform.aggregate;

import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.common.BaseAggregateRoot;
import site.hexaarch.ecommerce.logistics.domain.platform.event.PlatformIntegrationCreatedEvent;
import site.hexaarch.ecommerce.logistics.domain.platform.event.PlatformIntegrationStatusChangedEvent;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformName;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 平台集成聚合根
 *
 * @author kenyon
 */
@Getter
public class PlatformIntegration extends BaseAggregateRoot {

    private UUID id;
    private String tenantId;
    private PlatformName platformName;
    private String apiKey;
    private String apiSecret;
    private String storeUrl;
    private PlatformStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private PlatformIntegration() {
        // Private constructor for builder
    }

    public static Builder builder() {
        return new Builder();
    }

    public void activate() {
        if (this.status != PlatformStatus.ACTIVE) {
            this.status = PlatformStatus.ACTIVE;
            this.updatedAt = LocalDateTime.now();
            registerDomainEvent(new PlatformIntegrationStatusChangedEvent(this.id, this.status));
        }
    }

    public void deactivate() {
        if (this.status == PlatformStatus.ACTIVE) {
            this.status = PlatformStatus.INACTIVE;
            this.updatedAt = LocalDateTime.now();
            registerDomainEvent(new PlatformIntegrationStatusChangedEvent(this.id, this.status));
        }
    }

    public void create() {
        this.id = UUID.randomUUID();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = PlatformStatus.INACTIVE;
        registerDomainEvent(new PlatformIntegrationCreatedEvent(this.id, this.tenantId, this.platformName));
    }

    public static class Builder {
        private final PlatformIntegration platformIntegration = new PlatformIntegration();

        public Builder id(UUID id) {
            platformIntegration.id = id;
            return this;
        }

        public Builder tenantId(String tenantId) {
            platformIntegration.tenantId = tenantId;
            return this;
        }

        public Builder platformName(PlatformName platformName) {
            platformIntegration.platformName = platformName;
            return this;
        }

        public Builder apiKey(String apiKey) {
            platformIntegration.apiKey = apiKey;
            return this;
        }

        public Builder apiSecret(String apiSecret) {
            platformIntegration.apiSecret = apiSecret;
            return this;
        }

        public Builder storeUrl(String storeUrl) {
            platformIntegration.storeUrl = storeUrl;
            return this;
        }

        public Builder status(PlatformStatus status) {
            platformIntegration.status = status;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            platformIntegration.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(LocalDateTime updatedAt) {
            platformIntegration.updatedAt = updatedAt;
            return this;
        }

        public PlatformIntegration build() {
            return platformIntegration;
        }
    }
}
