package site.hexaarch.ecommerce.logistics.application.service.warehouse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.platform.aggregate.PlatformIntegration;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformCredential;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformWarehouseFetchRequest;
import site.hexaarch.ecommerce.logistics.domain.platform.client.PlatformWarehouseFetcher;
import site.hexaarch.ecommerce.logistics.domain.platform.repository.PlatformIntegrationRepository;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.PlatformFetcherRegistry;
import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.ShopWarehouse;
import site.hexaarch.ecommerce.logistics.domain.warehouse.aggregate.Warehouse;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.ShopWarehouseRepository;
import site.hexaarch.ecommerce.logistics.domain.warehouse.repository.WarehouseRepository;
import site.hexaarch.ecommerce.logistics.interfaces.dto.warehouseimport.BindShopWarehouseRequest;
import site.hexaarch.ecommerce.logistics.interfaces.dto.warehouseimport.ShopWarehouseDto;
import site.hexaarch.ecommerce.logistics.infrastructure.platform.oauth.PlatformCredentials;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 店铺仓库应用服务：维护第三方电商平台店铺仓库与本地仓库的绑定关系。
 *
 * @author kenyon
 */
@Slf4j
@Service
public class ShopWarehouseApplicationService {

    public static final String DEFAULT_TENANT_ID = "default-tenant";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ShopWarehouseRepository shopWarehouseRepository;
    private final WarehouseRepository warehouseRepository;
    private final PlatformIntegrationRepository platformIntegrationRepository;
    private final PlatformFetcherRegistry fetcherRegistry;
    private final PlatformCredentials platformCredentials;

    public ShopWarehouseApplicationService(ShopWarehouseRepository shopWarehouseRepository,
                                           WarehouseRepository warehouseRepository,
                                           PlatformIntegrationRepository platformIntegrationRepository,
                                           PlatformFetcherRegistry fetcherRegistry,
                                           PlatformCredentials platformCredentials) {
        this.shopWarehouseRepository = shopWarehouseRepository;
        this.warehouseRepository = warehouseRepository;
        this.platformIntegrationRepository = platformIntegrationRepository;
        this.fetcherRegistry = fetcherRegistry;
        this.platformCredentials = platformCredentials;
    }

    /**
     * 绑定（或更新绑定）店铺仓库到本地仓库
     */
    @Transactional
    public ShopWarehouseDto bindShopWarehouse(BindShopWarehouseRequest request) {
        String tenantId = resolveTenant(request.getTenantId());
        if (!StringUtils.hasText(request.getPlatformWarehouseId())) {
            throw new site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException(
                    "平台仓库ID不能为空");
        }
        if (!StringUtils.hasText(request.getLocalWarehouseId())) {
            throw new site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException(
                    "本地仓库ID不能为空");
        }
        Warehouse localWarehouse = warehouseRepository.findById(request.getLocalWarehouseId())
                .orElseThrow(() -> new EntityNotFoundException("本地仓库不存在: " + request.getLocalWarehouseId()));

        String platform = resolvePlatform(request.getPlatform(), null);
        ShopWarehouse shopWarehouse = shopWarehouseRepository
                .findByTenantIdAndPlatformAndPlatformWarehouseId(tenantId, platform, request.getPlatformWarehouseId())
                .orElseGet(() -> ShopWarehouse.create(tenantId, platform, request.getIntegrationId(),
                        request.getShopCode(), request.getShopName(), request.getPlatformWarehouseId(),
                        request.getPlatformWarehouseName(), request.getAddress(),
                        Boolean.TRUE.equals(request.getDefaultWarehouse())));
        shopWarehouse.bindToLocalWarehouse(localWarehouse.getWarehouseId(), localWarehouse.getWarehouseCode());
        if (StringUtils.hasText(request.getShopCode())) {
            shopWarehouse.updateFromPlatform(request.getShopName(), request.getPlatformWarehouseName(),
                    request.getAddress(), request.getDefaultWarehouse(), request.getIntegrationId());
        }
        return toDto(shopWarehouseRepository.save(shopWarehouse));
    }

    /**
     * 解除店铺仓库与本地仓库的绑定
     */
    @Transactional
    public ShopWarehouseDto unbindShopWarehouse(String id) {
        ShopWarehouse shopWarehouse = getShopWarehouseEntity(id);
        shopWarehouse.unbind();
        return toDto(shopWarehouseRepository.save(shopWarehouse));
    }

    /**
     * 从平台拉取店铺仓库并按「本地仓库编码」自动绑定
     *
     * @return 同步结果（新增/更新/失败统计）
     */
    @Transactional
    public Map<String, Object> syncShopWarehousesFromPlatform(String tenantId, String integrationId,
                                                              Boolean useSampleData, String keyword, Integer limit) {
        String resolvedTenant = resolveTenant(tenantId);
        PlatformIntegration integration = resolveIntegration(integrationId);
        String platform = integration == null ? null
                : (integration.getPlatformName() == null ? null : integration.getPlatformName().name());
        if (!StringUtils.hasText(platform)) {
            throw new site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException(
                    "无法确定平台名称，请提供有效的平台集成ID");
        }

        boolean useSample = Boolean.TRUE.equals(useSampleData);
        if (!useSample && integration == null) {
            throw new site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException(
                    "尚未选择平台集成(integrationId)，无法拉取店铺仓库；可勾选「使用示例数据」重试");
        }
        PlatformCredential credential = platformCredentials.resolve(integration);
        PlatformWarehouseFetcher fetcher = fetcherRegistry.warehouseFetcher(credential, useSample);
        List<Map<String, Object>> rawRows = fetcher.fetch(new PlatformWarehouseFetchRequest(
                resolvedTenant, platform,
                integration == null ? null : integration.getStoreUrl(),
                integration == null ? null : integration.getApiKey(),
                integration == null ? null : integration.getApiSecret(),
                keyword, limit, 1), credential);

        // 本地仓库编码索引，用于自动绑定
        Map<String, Warehouse> warehouseByCode = new HashMap<>();
        for (Warehouse warehouse : warehouseRepository.findAll()) {
            if (warehouse.getWarehouseCode() != null) {
                warehouseByCode.put(warehouse.getWarehouseCode().toLowerCase(), warehouse);
            }
        }

        int created = 0;
        int updated = 0;
        int bound = 0;
        List<String> failures = new ArrayList<>();
        List<ShopWarehouseDto> synced = new ArrayList<>();

        for (int i = 0; i < rawRows.size(); i++) {
            Map<String, Object> rawRow = rawRows.get(i);
            String platformWarehouseId = firstText(rawRow, "platformWarehouseId", "platform_warehouse_id",
                    "warehouseId", "warehouse_id", "wid");
            if (!StringUtils.hasText(platformWarehouseId)) {
                failures.add("第" + (i + 1) + "行缺少平台仓库ID，已跳过");
                continue;
            }
            try {
                var existing = shopWarehouseRepository
                        .findByTenantIdAndPlatformAndPlatformWarehouseId(resolvedTenant, platform, platformWarehouseId);
                boolean isNew = existing.isEmpty();
                ShopWarehouse shopWarehouse = existing
                        .orElseGet(() -> ShopWarehouse.create(resolvedTenant, platform,
                                integration == null ? null : String.valueOf(integration.getId()),
                                firstText(rawRow, "shopCode", "shop_code", "storeCode", "store_code"),
                                firstText(rawRow, "shopName", "shop_name", "storeName", "store_name"),
                                platformWarehouseId,
                                firstText(rawRow, "platformWarehouseName", "platform_warehouse_name",
                                        "warehouseName", "warehouse_name", "name", "title"),
                                firstText(rawRow, "address", "location"),
                                Boolean.parseBoolean(firstText(rawRow, "isDefault", "is_default", "default"))));

                shopWarehouse.updateFromPlatform(
                        firstText(rawRow, "shopName", "shop_name", "storeName", "store_name"),
                        firstText(rawRow, "platformWarehouseName", "platform_warehouse_name",
                                "warehouseName", "warehouse_name", "name", "title"),
                        firstText(rawRow, "address", "location"),
                        parseBoolean(firstText(rawRow, "isDefault", "is_default", "default")),
                        integration == null ? null : String.valueOf(integration.getId()));

                // 按本地仓库编码自动绑定：平台原始行中 warehouseCode/localWarehouseCode 命中本地仓库即绑定
                String localCode = firstText(rawRow, "warehouseCode", "warehouse_code", "localWarehouseCode");
                if (StringUtils.hasText(localCode) && !shopWarehouse.isBound()) {
                    Warehouse local = warehouseByCode.get(localCode.toLowerCase());
                    if (local != null) {
                        shopWarehouse.bindToLocalWarehouse(local.getWarehouseId(), local.getWarehouseCode());
                        bound++;
                    }
                }
                shopWarehouseRepository.save(shopWarehouse);
                if (isNew) {
                    created++;
                } else {
                    updated++;
                }
                synced.add(toDto(shopWarehouse));
            } catch (Exception e) {
                log.warn("[ShopWarehouseSync] 第{}行同步失败: {}", i + 1, e.getMessage());
                failures.add("第" + (i + 1) + "行同步失败: " + e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("platform", platform);
        result.put("integrationId", integrationId);
        result.put("totalFetched", rawRows.size());
        result.put("createdCount", created);
        result.put("updatedCount", updated);
        result.put("autoBoundCount", bound);
        result.put("failureCount", failures.size());
        result.put("failures", failures);
        result.put("shopWarehouses", synced);
        return result;
    }

    /**
     * 查询店铺仓库列表
     */
    public List<ShopWarehouseDto> listShopWarehouses(String tenantId, String platform, String localWarehouseId) {
        String resolvedTenant = resolveTenant(tenantId);
        List<ShopWarehouse> shopWarehouses;
        if (StringUtils.hasText(localWarehouseId)) {
            shopWarehouses = shopWarehouseRepository.findByLocalWarehouseId(localWarehouseId);
        } else if (StringUtils.hasText(platform)) {
            shopWarehouses = shopWarehouseRepository.findByTenantIdAndPlatform(resolvedTenant,
                    platform.trim().toUpperCase());
        } else {
            shopWarehouses = shopWarehouseRepository.findByTenantId(resolvedTenant);
        }
        return shopWarehouses.stream().map(this::toDto).toList();
    }

    /**
     * 查询店铺仓库详情
     */
    public ShopWarehouseDto getShopWarehouse(String id) {
        return toDto(getShopWarehouseEntity(id));
    }

    private ShopWarehouse getShopWarehouseEntity(String id) {
        return shopWarehouseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("店铺仓库不存在: " + id));
    }

    private ShopWarehouseDto toDto(ShopWarehouse shopWarehouse) {
        ShopWarehouseDto dto = new ShopWarehouseDto();
        dto.setId(shopWarehouse.getId());
        dto.setTenantId(shopWarehouse.getTenantId());
        dto.setPlatform(shopWarehouse.getPlatform());
        dto.setIntegrationId(shopWarehouse.getIntegrationId());
        dto.setShopCode(shopWarehouse.getShopCode());
        dto.setShopName(shopWarehouse.getShopName());
        dto.setPlatformWarehouseId(shopWarehouse.getPlatformWarehouseId());
        dto.setPlatformWarehouseName(shopWarehouse.getPlatformWarehouseName());
        dto.setLocalWarehouseId(shopWarehouse.getLocalWarehouseId());
        dto.setLocalWarehouseCode(shopWarehouse.getLocalWarehouseCode());
        dto.setLocalWarehouseName(shopWarehouse.getLocalWarehouseId() == null ? null :
                warehouseRepository.findById(shopWarehouse.getLocalWarehouseId())
                        .map(Warehouse::getWarehouseName).orElse(null));
        dto.setAddress(shopWarehouse.getAddress());
        dto.setDefaultWarehouse(shopWarehouse.isDefaultWarehouse());
        dto.setActive(shopWarehouse.isActive());
        dto.setBound(shopWarehouse.isBound());
        dto.setLastSyncedAt(format(shopWarehouse.getLastSyncedAt()));
        dto.setCreatedAt(format(shopWarehouse.getCreatedAt()));
        dto.setUpdatedAt(format(shopWarehouse.getUpdatedAt()));
        return dto;
    }

    private PlatformIntegration resolveIntegration(String integrationId) {
        if (!StringUtils.hasText(integrationId)) {
            return null;
        }
        UUID id;
        try {
            id = UUID.fromString(integrationId);
        } catch (IllegalArgumentException e) {
            throw new site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException(
                    "平台集成ID格式不正确: " + integrationId);
        }
        return platformIntegrationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("平台集成不存在: " + integrationId));
    }

    private String resolvePlatform(String platform, PlatformIntegration integration) {
        if (StringUtils.hasText(platform)) {
            return platform.trim().toUpperCase();
        }
        if (integration != null && integration.getPlatformName() != null) {
            return integration.getPlatformName().name();
        }
        throw new site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException(
                "平台名称不能为空");
    }

    private String resolveTenant(String tenantId) {
        return StringUtils.hasText(tenantId) ? tenantId.trim() : DEFAULT_TENANT_ID;
    }

    private String firstText(Map<String, Object> row, String... keys) {
        if (row == null) {
            return null;
        }
        for (String key : keys) {
            Object value = row.get(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))
                    && !"null".equalsIgnoreCase(String.valueOf(value).trim())) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    private Boolean parseBoolean(String value) {
        return value == null ? null : Boolean.parseBoolean(value);
    }

    private String format(java.time.LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(FORMATTER);
    }
}
