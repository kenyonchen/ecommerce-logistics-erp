package site.hexaarch.ecommerce.logistics.infrastructure.platform.shopee;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ShopeeSignature} 的已知向量与拼接口径测试。
 * <p>
 * 期望 hex 由独立的 HMAC-SHA256 实现（python {@code hmac} 模块）离线算出后硬编码：一旦有人把拼接顺序
 * 改成「path + partner_id + timestamp」那种常见写法，或把 hex 改成大写，这里都会立刻红。
 *
 * @author kenyon
 */
class ShopeeSignatureTest {

    private static final String PARTNER_ID = "12345";
    private static final String PARTNER_KEY = "secret_key_xyz";
    private static final String ACCESS_TOKEN = "token_abc";
    private static final String SHOP_ID = "67890";
    private static final long TIMESTAMP = 1700000000L;

    @Test
    void shouldConcatShopLevelBaseStringInPartnerIdPathTimestampTokenShopOrder() {
        String base = ShopeeSignature.baseString(PARTNER_ID, "/api/v2/order/get_order_list",
                TIMESTAMP, ACCESS_TOKEN, SHOP_ID);

        // §6：partner_id + apiPath + timestamp + access_token + shop_id，无分隔符
        assertEquals("12345/api/v2/order/get_order_list1700000000token_abc67890", base);
    }

    @Test
    void shouldMatchIndependentHmacForShopLevelKnownVector() {
        String base = ShopeeSignature.baseString(PARTNER_ID, "/api/v2/order/get_order_list",
                TIMESTAMP, ACCESS_TOKEN, SHOP_ID);

        String sign = ShopeeSignature.sign(PARTNER_KEY, base);

        // HMAC-SHA256(key=secret_key_xyz, base) 的离线结果，独立算出后硬编码：改顺序/改大小写都会红
        assertEquals("c232fb8b57f0ef172e28d13accc0d213a938ca07d2cb4a3389350aedd31bd0a9", sign);
    }

    @Test
    void shouldProduceLowercaseHexOfFixedLength() {
        String sign = ShopeeSignature.sign(PARTNER_KEY, ShopeeSignature.baseString(PARTNER_ID,
                "/api/v2/order/get_order_list", TIMESTAMP, ACCESS_TOKEN, SHOP_ID));

        assertTrue(sign.matches("[0-9a-f]{64}"), "§6：sign 是 HMAC-SHA256 的 64 位全小写 hex，实际=" + sign);
    }

    @Test
    void shouldConcatPublicLevelBaseStringWithoutTokenOrShop() {
        String base = ShopeeSignature.baseString(PARTNER_ID, "/api/v2/auth/access_token/get", TIMESTAMP);

        // §6：Public 级不拼 token；换取时尚无 access_token
        assertEquals("12345/api/v2/auth/access_token/get1700000000", base);
    }

    @Test
    void shouldMatchIndependentHmacForPublicLevelKnownVector() {
        String sign = ShopeeSignature.sign(PARTNER_KEY,
                ShopeeSignature.baseString(PARTNER_ID, "/api/v2/auth/access_token/get", TIMESTAMP));

        assertEquals("3f27fb4839e4b8aff5b439ac57ad687c97d5de2551fb42c72bd815e5b5c45ddb", sign);
    }

    @Test
    void shouldTreatNullTokenAndShopAsEmptyInsteadOfSkipping() {
        String base = ShopeeSignature.baseString(PARTNER_ID, "/api/v2/order/get_order_list", TIMESTAMP, null, null);

        assertEquals("12345/api/v2/order/get_order_list1700000000", base);
    }
}
