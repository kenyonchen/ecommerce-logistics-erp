package site.hexaarch.ecommerce.logistics.domain.shop.aggregate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.hexaarch.ecommerce.logistics.domain.common.exception.ValidationException;
import site.hexaarch.ecommerce.logistics.domain.shop.valueobject.PushBackPolicy;
import site.hexaarch.ecommerce.logistics.domain.shop.valueobject.ShopAuthStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 店铺聚合单元测试。
 * <p>
 * 重点盯 live probe 实测出来的那个坑：{@code region} 入参 {@code na} 原样入库，而
 * {@code AMAZON_REGIONS} 与外呼选 host 都按大写比对，同一站点在库里出现两种取值。
 * 归一化收敛到 {@link Shop#normalizeRegion} 后，写入侧（create 与 bindCredentials）与
 * 校验侧共用一个实现，这里把两侧钉住。
 * </p>
 *
 * @author kenyon
 */
class ShopTest {

    private static final String TENANT = "tenant-a";

    @Test
    @DisplayName("建店时 region 归一为大写，platform 也归一为大写")
    void createNormalizesRegionAndPlatformToUpperCase() {
        Shop shop = Shop.create(TENANT, null, "amazon", "QA-01", "探针店", "na", null);

        assertEquals("AMAZON", shop.getPlatform());
        assertEquals("NA", shop.getRegion(), "region 必须落库为大写，否则 host 路由按大小写分叉");
        assertEquals(ShopAuthStatus.PENDING, shop.getAuthStatus());
        assertEquals(PushBackPolicy.MANUAL, shop.getPushBackPolicy());
        assertTrue(shop.isActive());
        assertFalse(shop.hasAnyCredential());
    }

    @Test
    @DisplayName("region 传空串等同于不绑定站点，落库为 null 而非空串")
    void createTreatsBlankRegionAsNull() {
        assertNull(Shop.create(TENANT, null, "SHOPEE", "QA-02", "空站点店", "  ", null).getRegion());
    }

    @Test
    @DisplayName("normalizeRegion 对空白返回 null，对大小写混写返回大写")
    void normalizeRegionCollapsesCaseAndBlank() {
        assertNull(Shop.normalizeRegion(null));
        assertNull(Shop.normalizeRegion("   "));
        assertEquals("EU", Shop.normalizeRegion(" eu "));
        assertEquals("FE", Shop.normalizeRegion("Fe"));
    }

    @Test
    @DisplayName("换凭据会重写 region 并保持大写归一，同时把授权状态打回 PENDING")
    void bindCredentialsReNormalizesRegionAndResetsAuthStatus() {
        Shop shop = Shop.create(TENANT, null, "AMAZON", "QA-03", "换令牌店", "NA", "ATVPDKIKX0DER");
        shop.markAuthActive();
        assertEquals(ShopAuthStatus.ACTIVE, shop.getAuthStatus());

        shop.bindCredentials("AKIA1234567890", null, null, null, null,
                null, "eu", null, null, null);

        assertEquals("EU", shop.getRegion());
        assertEquals(ShopAuthStatus.PENDING, shop.getAuthStatus(),
                "换 token 等于换数据来源，此前的 ACTIVE 不再可信");
        assertTrue(shop.hasAnyCredential());
    }

    @Test
    @DisplayName("bindCredentials 的 null=保留、空串=清除语义")
    void bindCredentialsKeepsOnNullAndClearsOnBlank() {
        Shop shop = Shop.create(TENANT, null, "EBAY", "QA-04", "语义店", null, null);
        shop.bindCredentials("key-1", "secret-1", "token-1", null, null, null, null, null, null, null);

        shop.bindCredentials(null, "", null, null, null, null, null, null, null, null);

        assertEquals("key-1", shop.getApiKey(), "null 表示保留原值");
        assertNull(shop.getApiSecret(), "空串表示清除");
        assertEquals("token-1", shop.getAccessToken());
    }

    @Test
    @DisplayName("建店缺少必填字段时抛出指向字段的校验错误")
    void createRejectsMissingRequiredFields() {
        assertThrows(ValidationException.class, () -> Shop.create(null, null, "AMAZON", "X", "店", null, null));
        assertThrows(ValidationException.class, () -> Shop.create(TENANT, null, "  ", "X", "店", null, null));
        assertThrows(ValidationException.class, () -> Shop.create(TENANT, null, "AMAZON", " ", "店", null, null));
        assertThrows(ValidationException.class, () -> Shop.create(TENANT, null, "AMAZON", "X", null, null, null));
    }

    @Test
    @DisplayName("店铺编码是身份锚，rename 改不了它")
    void renameCannotChangeShopCode() {
        Shop shop = Shop.create(TENANT, null, "AMAZON", "QA-05", "原名", null, null);

        shop.rename("改名");

        assertEquals("改名", shop.getShopName());
        assertEquals("QA-05", shop.getShopCode());
    }

    @Test
    @DisplayName("回写策略三态互斥且 null 被拒绝")
    void changePushBackPolicyRejectsNull() {
        Shop shop = Shop.create(TENANT, null, "AMAZON", "QA-06", "策略店", null, null);

        shop.changePushBackPolicy(PushBackPolicy.OFF);
        assertEquals(PushBackPolicy.OFF, shop.getPushBackPolicy());
        assertThrows(ValidationException.class, () -> shop.changePushBackPolicy(null));
    }
}
