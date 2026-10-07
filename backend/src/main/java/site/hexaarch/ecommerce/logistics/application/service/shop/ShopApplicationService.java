package site.hexaarch.ecommerce.logistics.application.service.shop;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import site.hexaarch.ecommerce.logistics.application.command.shop.CreateShopCommand;
import site.hexaarch.ecommerce.logistics.application.command.shop.ShopCredentialCommand;
import site.hexaarch.ecommerce.logistics.domain.common.exception.EntityNotFoundException;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.platform.repository.PlatformIntegrationRepository;
import site.hexaarch.ecommerce.logistics.domain.shop.aggregate.Shop;
import site.hexaarch.ecommerce.logistics.domain.shop.repository.ShopRepository;
import site.hexaarch.ecommerce.logistics.domain.shop.valueobject.PushBackPolicy;
import site.hexaarch.ecommerce.logistics.infrastructure.messaging.DomainEventPublisher;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 店铺应用服务。
 * <p>
 * <b>本类的每一条 public 方法都以形参名恰好为 {@code tenantId} 的 String 参数开头</b>，
 * 这是 {@code TenantIsolationAspect} 生效的前提（它只认参数名，见 ADR-0013 §4.3 锁 1），
 * 同时每个方法自己再做一次非空断言：平台管理员在切面里是提前 return 的旁路路径，
 * 「tenantId 必填」这件事必须由服务层守住，不能指望切面（§4.4）。
 * </p>
 * <p>
 * 跨租户读取一律走 {@code findByIdAndTenantId}：查不到就是 404，而不是 403——
 * 用别人的 id 探测「这个店铺存在但不属于你」同样是泄漏。
 * </p>
 * <p>
 * 尚未落地的两个入口 {@code test-connection} / {@code sync-orders} / {@code push-fulfillment}
 * 依赖外呼凭据从 {@code platform_integrations} 切流到 {@code shops}（ADR-0013 §6 步骤 5），
 * 在切流完成前这里不提供半实现版本。
 * </p>
 *
 * @author kenyon
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopApplicationService {

    /**
     * Amazon SP-API 的三个区域 host 归属；填错区域会在外呼时才炸，写入口先挡一道。
     */
    private static final Set<String> AMAZON_REGIONS = Set.of("NA", "EU", "FE");

    private static final String PLATFORM_AMAZON = "AMAZON";

    private final ShopRepository shopRepository;
    private final PlatformIntegrationRepository platformIntegrationRepository;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 本租户店铺列表。
     *
     * @param platform 平台过滤，可空表示不过滤
     */
    public List<Shop> listShops(String tenantId, String platform) {
        requireTenantId(tenantId);
        if (!StringUtils.hasText(platform)) {
            return shopRepository.findByTenantId(tenantId);
        }
        return shopRepository.findByTenantIdAndPlatform(tenantId, platform);
    }

    /**
     * 单个店铺详情（出参脱敏由 DTO 负责）。
     */
    public Shop getShop(String tenantId, String shopId) {
        requireTenantId(tenantId);
        return requireShop(tenantId, shopId);
    }

    public long countShops(String tenantId) {
        requireTenantId(tenantId);
        return shopRepository.countByTenantId(tenantId);
    }

    /**
     * 按店铺编码定位（供订单归属、导入行按店解析使用）。
     */
    public Shop findShopByCode(String tenantId, String shopCode) {
        requireTenantId(tenantId);
        return shopRepository.findByTenantIdAndShopCode(tenantId, shopCode)
                .orElseThrow(() -> new EntityNotFoundException("店铺不存在: " + shopCode));
    }

    /**
     * 新建店铺：只落主数据，凭据列为空、授权状态为 PENDING。
     */
    @Transactional
    public Shop createShop(String tenantId, CreateShopCommand command) {
        requireTenantId(tenantId);
        if (command == null) {
            throw new ValidationException("店铺信息不能为空");
        }
        validateIntegration(tenantId, command.integrationId());
        String platform = normalizePlatform(command.platform());
        validateRegion(platform, command.region());

        String shopCode = trimmed(command.shopCode());
        if (shopRepository.existsByTenantIdAndShopCode(tenantId, shopCode, null)) {
            throw new ValidationException("店铺编码 " + shopCode + " 在本租户下已存在，请换一个");
        }
        String marketplaceId = trimmedToNull(command.marketplaceId());
        if (marketplaceId != null
                && shopRepository.existsByTenantIdAndPlatformAndMarketplaceId(tenantId, platform, marketplaceId)) {
            throw new ValidationException("站点 " + marketplaceId + " 已绑定到本租户的其他店铺");
        }

        Shop shop = Shop.create(tenantId, command.integrationId(), platform, shopCode,
                command.shopName(), command.region(), marketplaceId);
        Shop saved = shopRepository.save(shop);
        domainEventPublisher.publishEventsFrom(saved);
        log.info("[Shop] 租户 {} 新建店铺 {}（platform={} shopCode={}）",
                tenantId, saved.getId(), saved.getPlatform(), saved.getShopCode());
        return saved;
    }

    /**
     * 改主数据：店铺名与启停。<b>不含任何凭据字段</b>——那是
     * {@link #bindCredentials(String, String, ShopCredentialCommand)} 的专职端点（ADR-0013 §4.2）。
     */
    @Transactional
    public Shop updateShop(String tenantId, String shopId, String shopName, Boolean active) {
        requireTenantId(tenantId);
        Shop shop = requireShop(tenantId, shopId);
        if (StringUtils.hasText(shopName)) {
            shop.rename(shopName);
        }
        if (active != null && active != shop.isActive()) {
            shop.changeActive(active);
        }
        Shop saved = shopRepository.save(shop);
        domainEventPublisher.publishEventsFrom(saved);
        return saved;
    }

    /**
     * 改发货回写策略。只改这一级开关，不重写物流单上已有的单级推送状态。
     */
    @Transactional
    public Shop changePushBackPolicy(String tenantId, String shopId, PushBackPolicy policy) {
        requireTenantId(tenantId);
        Shop shop = requireShop(tenantId, shopId);
        shop.changePushBackPolicy(policy);
        Shop saved = shopRepository.save(shop);
        domainEventPublisher.publishEventsFrom(saved);
        log.info("[Shop] 店铺 {} 的发货回写策略改为 {}", shopId, policy);
        return saved;
    }

    /**
     * 写入卖家级凭据与站点身份。聚合内部会把授权状态打回 PENDING：换 token 等于换数据来源，
     * 此前的 ACTIVE 不再可信，必须重过连通性测试。
     */
    @Transactional
    public Shop bindCredentials(String tenantId, String shopId, ShopCredentialCommand command) {
        requireTenantId(tenantId);
        if (command == null) {
            throw new ValidationException("凭据信息不能为空");
        }
        Shop shop = requireShop(tenantId, shopId);
        validateRegion(shop.getPlatform(), command.region());

        String targetMarketplaceId = StringUtils.hasText(command.marketplaceId())
                ? command.marketplaceId().trim()
                : shop.getMarketplaceId();
        // 站点没变就不必查重，否则改别的字段会被自己的唯一键挡住
        if (StringUtils.hasText(targetMarketplaceId)
                && !targetMarketplaceId.equals(shop.getMarketplaceId())
                && shopRepository.existsByTenantIdAndPlatformAndMarketplaceId(
                tenantId, shop.getPlatform(), targetMarketplaceId)) {
            throw new ValidationException("站点 " + targetMarketplaceId + " 已绑定到本租户的其他店铺");
        }

        shop.bindCredentials(command.apiKey(), command.apiSecret(), command.accessToken(),
                command.refreshToken(), command.tokenExpiresAt(), command.marketplaceId(),
                command.region(), command.externalShopId(), command.storeUrl(), command.apiVersion());
        Shop saved = shopRepository.save(shop);
        domainEventPublisher.publishEventsFrom(saved);
        log.info("[Shop] 店铺 {} 的凭据已更新，授权状态回到 PENDING", shopId);
        return saved;
    }

    /**
     * 删除店铺。
     * <p>
     * 注意：订单侧的引用守卫（有订单挂着这个店铺时拒绝删除，ADR-0013 §4.2）在
     * {@code orders.shop_id} 落地的那一步补——此刻订单表还没有店铺维度，先挡也是空转。
     * </p>
     */
    @Transactional
    public void deleteShop(String tenantId, String shopId) {
        requireTenantId(tenantId);
        Shop shop = requireShop(tenantId, shopId);
        shopRepository.deleteByIdAndTenantId(shop.getId(), tenantId);
        log.info("[Shop] 租户 {} 删除店铺 {}（shopCode={}）", tenantId, shop.getId(), shop.getShopCode());
    }

    private Shop requireShop(String tenantId, String shopId) {
        return shopRepository.findByIdAndTenantId(shopId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("店铺不存在: " + shopId));
    }

    /**
     * 服务层自己守住「租户必填」。平台管理员走切面提前 return 的旁路，切面不会替它校验。
     */
    private void requireTenantId(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            throw new ValidationException("租户ID不能为空");
        }
    }

    private String normalizePlatform(String platform) {
        if (!StringUtils.hasText(platform)) {
            throw new ValidationException("平台标识不能为空");
        }
        return platform.trim().toUpperCase();
    }

    /**
     * 对接定义引用校验：悬空的 {@code integration_id} 会让外呼在取对接元信息时炸得莫名其妙，
     * 在写入口就报指向字段的错误。
     * <p>
     * 这里刻意用 {@code findByTenantId} 而不是 {@code findById}：后者不带租户条件，
     * 让 A 租户的店铺指向 B 租户的对接定义，等于借用别人的 app 凭据。租户的对接定义数量很小，
     * 内存匹配比新开一条无租户查询便宜。
     * </p>
     * <p>
     * 待办：ADR-0013 §6 步骤 4 把 {@code platform_integrations} 收敛成全局对接定义（去 {@code tenant_id}）后，
     * 本校验改回按 id  existence 检查。
     * </p>
     */
    private void validateIntegration(String tenantId, String integrationId) {
        if (!StringUtils.hasText(integrationId)) {
            return;
        }
        UUID id;
        try {
            id = UUID.fromString(integrationId.trim());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("对接定义ID格式不正确: " + integrationId);
        }
        boolean visible = platformIntegrationRepository.findByTenantId(tenantId).stream()
                .anyMatch(integration -> id.equals(integration.getId()));
        if (!visible) {
            throw new ValidationException("对接定义不存在或不属于本租户: " + integrationId);
        }
    }

    private void validateRegion(String platform, String region) {
        if (!StringUtils.hasText(region)) {
            return;
        }
        if (PLATFORM_AMAZON.equals(platform) && !AMAZON_REGIONS.contains(Shop.normalizeRegion(region))) {
            throw new ValidationException("Amazon 的区域(region)只能是 NA/EU/FE 之一，当前值: " + region);
        }
    }

    private String trimmed(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ValidationException("店铺编码不能为空");
        }
        return value.trim();
    }

    private String trimmedToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
