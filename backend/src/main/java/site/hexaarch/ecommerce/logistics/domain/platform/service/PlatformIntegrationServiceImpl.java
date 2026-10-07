package site.hexaarch.ecommerce.logistics.domain.platform.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import site.hexaarch.ecommerce.logistics.application.service.order.OrderImportApplicationService;
import site.hexaarch.ecommerce.logistics.application.service.product.ProductImportApplicationService;
import site.hexaarch.ecommerce.logistics.domain.common.exception.BusinessException;
import site.hexaarch.ecommerce.logistics.domain.platform.aggregate.PlatformIntegration;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthCatalog;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthSpec;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformAuthType;
import site.hexaarch.ecommerce.logistics.domain.platform.valueobject.PlatformStatus;
import site.hexaarch.ecommerce.logistics.domain.platform.repository.PlatformIntegrationRepository;
import site.hexaarch.ecommerce.logistics.domain.warehouse.entity.InventoryRecord;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.InventoryRecordRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.config.HttpClientConfig;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 平台集成服务实现类
 *
 * @author kenyon
 */
@Slf4j
@Service
public class PlatformIntegrationServiceImpl implements PlatformIntegrationService {

    private final PlatformIntegrationRepository platformIntegrationRepository;
    private final InventoryRecordRepository inventoryRecordRepository;
    private final RestClient restClient;

    @Autowired
    public PlatformIntegrationServiceImpl(PlatformIntegrationRepository platformIntegrationRepository,
                                          InventoryRecordRepository inventoryRecordRepository,
                                          RestClient.Builder restClientBuilder,
                                          @Qualifier(HttpClientConfig.PLATFORM_REQUEST_FACTORY)
                                          ClientHttpRequestFactory requestFactory) {
        this.platformIntegrationRepository = platformIntegrationRepository;
        this.inventoryRecordRepository = inventoryRecordRepository;
        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public PlatformIntegration createPlatformIntegration(PlatformIntegration platformIntegration) {
        PlatformIntegration normalized = normalizeAndValidate(platformIntegration);
        normalized.create();
        return platformIntegrationRepository.save(normalized);
    }

    @Override
    public PlatformIntegration getPlatformIntegrationById(UUID id) {
        Optional<PlatformIntegration> optionalPlatform = platformIntegrationRepository.findById(id);
        return optionalPlatform.orElse(null);
    }

    @Override
    public List<PlatformIntegration> getAllPlatformIntegrations() {
        return platformIntegrationRepository.findAll();
    }

    @Override
    public List<PlatformIntegration> getPlatformIntegrationsByTenant(String tenantId) {
        return platformIntegrationRepository.findByTenantId(tenantId);
    }

    @Override
    public PlatformIntegration updatePlatformIntegration(UUID id, PlatformIntegration platformIntegration) {
        Optional<PlatformIntegration> optionalExisting = platformIntegrationRepository.findById(id);
        if (optionalExisting.isPresent()) {
            PlatformIntegration existing = optionalExisting.get();
            PlatformIntegration updated = PlatformIntegration.builder()
                    .id(existing.getId())
                    .tenantId(existing.getTenantId())
                    .platformName(platformIntegration.getPlatformName())
                    .authType(platformIntegration.getAuthType() == null
                            ? existing.getAuthType() : platformIntegration.getAuthType())
                    .apiKey(platformIntegration.getApiKey())
                    .apiSecret(platformIntegration.getApiSecret())
                    .accessToken(platformIntegration.getAccessToken())
                    .refreshToken(platformIntegration.getRefreshToken())
                    .tokenExpiresAt(platformIntegration.getTokenExpiresAt())
                    .apiVersion(platformIntegration.getApiVersion())
                    .storeUrl(platformIntegration.getStoreUrl())
                    .region(platformIntegration.getRegion())
                    .externalShopId(platformIntegration.getExternalShopId())
                    .marketplaceIds(platformIntegration.getMarketplaceIds())
                    .status(platformIntegration.getStatus() == null
                            ? existing.getStatus() : platformIntegration.getStatus())
                    .createdAt(existing.getCreatedAt())
                    .updatedAt(platformIntegration.getUpdatedAt())
                    .lastProductsSyncedAt(existing.getLastProductsSyncedAt())
                    .lastOrdersSyncedAt(existing.getLastOrdersSyncedAt())
                    .build();
            return platformIntegrationRepository.save(normalizeAndValidate(updated));
        }
        return null;
    }

    @Override
    public void deletePlatformIntegration(UUID id) {
        platformIntegrationRepository.deleteById(id);
    }

    @Override
    public PlatformIntegration activatePlatformIntegration(UUID id) {
        Optional<PlatformIntegration> optionalPlatform = platformIntegrationRepository.findById(id);
        if (optionalPlatform.isPresent()) {
            PlatformIntegration platform = optionalPlatform.get();
            platform.activate();
            return platformIntegrationRepository.save(platform);
        }
        return null;
    }

    @Override
    public PlatformIntegration deactivatePlatformIntegration(UUID id) {
        Optional<PlatformIntegration> optionalPlatform = platformIntegrationRepository.findById(id);
        if (optionalPlatform.isPresent()) {
            PlatformIntegration platform = optionalPlatform.get();
            platform.deactivate();
            return platformIntegrationRepository.save(platform);
        }
        return null;
    }

    /**
     * 商品同步已迁移至 {@link site.hexaarch.ecommerce.logistics.application.service.ProductImportApplicationService}，
     * 这里仅更新平台的最后商品同步时间。
     */
    @Override
    public java.time.LocalDateTime markProductsSynced(UUID platformId) {
        Optional<PlatformIntegration> optionalPlatform = platformIntegrationRepository.findById(platformId);
        if (optionalPlatform.isEmpty()) {
            throw new IllegalArgumentException("Platform integration not found: " + platformId);
        }
        PlatformIntegration platform = optionalPlatform.get();
        platform.markProductsSyncedAt();
        return platformIntegrationRepository.save(platform).getLastProductsSyncedAt();
    }

    /**
     * 订单同步已迁移至 {@link site.hexaarch.ecommerce.logistics.application.service.OrderImportApplicationService}，
     * 这里仅更新平台的最后订单同步时间。
     */
    @Override
    public java.time.LocalDateTime markOrdersSynced(UUID platformId) {
        Optional<PlatformIntegration> optionalPlatform = platformIntegrationRepository.findById(platformId);
        if (optionalPlatform.isEmpty()) {
            throw new IllegalArgumentException("Platform integration not found: " + platformId);
        }
        PlatformIntegration platform = optionalPlatform.get();
        platform.markOrdersSyncedAt();
        return platformIntegrationRepository.save(platform).getLastOrdersSyncedAt();
    }

    /**
     * 库存推送到平台：汇总系统内库存（按SKU合计数量），POST {storeUrl}/inventory 推送，
     * 鉴权头 X-API-Key / X-API-Secret 与拉取接口保持同一约定。
     * <p>
     * 这是 {@code CUSTOM_SHOP} 协议的约定端点，没有走适配器注册表；其他协议必须先补
     * {@code PlatformInventoryPusher} 端口，否则会向平台不存在的路径推送并误报成功。
     */
    @Override
    public InventorySyncResult syncInventoryToPlatform(UUID platformId) {
        Optional<PlatformIntegration> optionalPlatform = platformIntegrationRepository.findById(platformId);
        if (optionalPlatform.isEmpty()) {
            throw new BusinessException("平台集成不存在: " + platformId, "PLATFORM_INTEGRATION_NOT_FOUND");
        }
        PlatformIntegration platform = optionalPlatform.get();
        if (platform.getStatus() != PlatformStatus.ACTIVE) {
            throw new BusinessException("平台集成未启用，无法同步库存", "PLATFORM_INTEGRATION_INACTIVE");
        }
        if (platform.getAuthType() != null && platform.getAuthType() != PlatformAuthType.CUSTOM_SHOP) {
            throw new BusinessException("协议 " + platform.getAuthType().name()
                    + " 尚未实现库存推送：Shopify 需要先把本地 SKU 映射到 inventory_item_id 与 location_id，"
                    + "当前表结构没有该映射", "PLATFORM_INVENTORY_PUSH_UNSUPPORTED");
        }
        String storeUrl = platform.getStoreUrl();
        if (storeUrl == null || storeUrl.isBlank()) {
            throw new BusinessException("平台店铺地址(storeUrl)未配置，无法同步库存", "PLATFORM_STORE_URL_MISSING");
        }

        // 按SKU汇总数量
        Map<String, Integer> skuQuantities = new LinkedHashMap<>();
        for (InventoryRecord record : inventoryRecordRepository.findAll()) {
            if (record.getSku() == null || record.getSku().isBlank()) {
                continue;
            }
            skuQuantities.merge(record.getSku(), Math.max(record.getQuantity(), 0), Integer::sum);
        }

        List<InventorySyncResult.SkuQuantity> items = new ArrayList<>();
        skuQuantities.forEach((sku, quantity) -> items.add(new InventorySyncResult.SkuQuantity(sku, quantity)));

        String baseUrl = normalizeUrl(storeUrl);
        try {
            restClient.post()
                    .uri(baseUrl + "/inventory")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header("X-API-Key", platform.getApiKey() == null ? "" : platform.getApiKey())
                    .header("X-API-Secret", platform.getApiSecret() == null ? "" : platform.getApiSecret())
                    .body(Map.of(
                            "tenantId", platform.getTenantId(),
                            "platform", platform.getPlatformName() == null ? "" : platform.getPlatformName().name(),
                            "items", items
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("[PlatformIntegration] 库存推送失败 platform={} url={} err={}",
                    platform.getPlatformName(), baseUrl, e.getMessage());
            throw new BusinessException("库存推送到平台失败: " + e.getMessage(), "PLATFORM_INVENTORY_PUSH_FAILED");
        }

        log.info("[PlatformIntegration] 库存推送成功 platform={} skus={}", platform.getPlatformName(), items.size());
        return new InventorySyncResult(platform.getId() == null ? null : platform.getId().toString(), platform.getPlatformName() == null ? null : platform.getPlatformName().name(),
                storeUrl, items, LocalDateTime.now());
    }

    /**
     * 按对接协议校验必填凭据并归一化缺省值。
     * <p>
     * 校验规则来自 {@link PlatformAuthCatalog}（协议 -> 必填字段），而不是在这里按协议堆 if-else：
     * 新增一家平台只改目录一处，配置界面与后端校验读的是同一张表，不会出现「界面让填、后端不收」。
     * 校验放在领域服务而非 DTO 层，因为 {@code CUSTOM_SHOP} 是 {@code authType} 为空时的默认协议，
     * 需要与外呼侧的解析口径保持一致。
     */
    private PlatformIntegration normalizeAndValidate(PlatformIntegration integration) {
        if (integration.getPlatformName() == null) {
            throw new BusinessException("平台名称(platformName)不能为空", "PLATFORM_NAME_REQUIRED");
        }
        PlatformAuthType authType = integration.getAuthType() == null
                ? PlatformAuthType.CUSTOM_SHOP : integration.getAuthType();
        PlatformAuthSpec spec = PlatformAuthCatalog.of(authType);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("storeUrl", integration.getStoreUrl());
        values.put("apiKey", integration.getApiKey());
        values.put("apiSecret", integration.getApiSecret());
        values.put("accessToken", integration.getAccessToken());
        values.put("refreshToken", integration.getRefreshToken());
        values.put("externalShopId", integration.getExternalShopId());
        values.put("marketplaceIds", integration.getMarketplaceIds());
        values.put("region", integration.getRegion());
        for (String field : spec.requiredFields()) {
            if (isBlank(values.get(field))) {
                // 错误码按字段生成（PLATFORM_ACCESS_TOKEN_REQUIRED 等），既保住表驱动校验的通用性，
                // 又保住调用方按错误码分支的能力——统一成一个 PLATFORM_FIELD_REQUIRED 会把可辨识性丢掉
                throw new BusinessException(fieldLabel(field) + " 是 " + spec.label() + "(" + authType.name()
                        + ") 的必填项", "PLATFORM_" + toSnakeCase(field) + "_REQUIRED");
            }
        }
        String region = trimToNull(integration.getRegion());
        if (region != null && !spec.regionOptions().isEmpty()
                && spec.regionOptions().stream().noneMatch(option -> option.equalsIgnoreCase(region))) {
            throw new BusinessException("region=" + region + " 不在 " + spec.label() + " 支持的取值内，可选: "
                    + spec.regionOptions(), "PLATFORM_REGION_UNSUPPORTED");
        }
        // apiKey/apiSecret 两列有 NOT NULL 约束：Admin API 一类只认访问令牌的平台未填时用空串占位
        return PlatformIntegration.builder()
                .id(integration.getId())
                .tenantId(integration.getTenantId())
                .platformName(integration.getPlatformName())
                .authType(authType)
                .apiKey(integration.getApiKey() == null ? "" : integration.getApiKey())
                .apiSecret(integration.getApiSecret() == null ? "" : integration.getApiSecret())
                .accessToken(integration.getAccessToken())
                .refreshToken(integration.getRefreshToken())
                .tokenExpiresAt(integration.getTokenExpiresAt())
                .apiVersion(integration.getApiVersion())
                .storeUrl(integration.getStoreUrl())
                .region(region)
                .externalShopId(trimToNull(integration.getExternalShopId()))
                .marketplaceIds(trimToNull(integration.getMarketplaceIds()))
                .status(integration.getStatus())
                .createdAt(integration.getCreatedAt())
                .updatedAt(integration.getUpdatedAt())
                .lastProductsSyncedAt(integration.getLastProductsSyncedAt())
                .lastOrdersSyncedAt(integration.getLastOrdersSyncedAt())
                .build();
    }

    private static String fieldLabel(String field) {
        return switch (field) {
            case "storeUrl" -> "平台店铺地址(storeUrl)";
            case "apiKey" -> "API Key(apiKey)";
            case "apiSecret" -> "API Secret(apiSecret)";
            case "accessToken" -> "访问令牌(accessToken)";
            case "refreshToken" -> "刷新令牌(refreshToken)";
            case "externalShopId" -> "平台侧店铺标识(externalShopId)";
            case "marketplaceIds" -> "市场标识(marketplaceIds)";
            case "region" -> "区域(region)";
            default -> field;
        };
    }

    /**
     * camelCase -> UPPER_SNAKE，用于按字段生成错误码（{@code accessToken} -> {@code ACCESS_TOKEN}）。
     */
    static String toSnakeCase(String field) {
        StringBuilder snake = new StringBuilder(field.length() + 4);
        for (int i = 0; i < field.length(); i++) {
            char c = field.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                snake.append('_');
            }
            snake.append(Character.toUpperCase(c));
        }
        return snake.toString();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeUrl(String storeUrl) {
        String url = storeUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        return url;
    }
}
