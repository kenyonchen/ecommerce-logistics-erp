package site.hexaarch.ecommerce.logistics.infrastructure.platform.shopee;

import site.hexaarch.ecommerce.logistics.infrastructure.platform.common.PlatformSignatures;

/**
 * Shopee Open Platform v2 请求签名：把 {@code partner_id/apiPath/timestamp/access_token/shop_id} 拼成待签串，
 * 再用 {@code partner_key} 作 HMAC-SHA256 密钥取<b>小写 hex</b>。
 * <p>
 * 两个必须写死、不能按行业惯例「顺手改」的坑（docs/spec/platform-api-reference.md §6）：
 * <ul>
 *   <li><b>拼接顺序</b>是 {@code partner_id + apiPath + timestamp}，Shop 级接口再拼 {@code access_token + shop_id}；
 *       这与最常见的「{@code path + partner_id + timestamp}」写法<b>顺序不同</b>，顺序错了签名就对不上、
 *       每次调用都会被平台判为 {@code error_signature}；</li>
 *   <li><b>大小写</b>：{@code sign = HMAC_SHA256(key = partner_key, base)} 输出<b>全小写</b> hex，
 *       而不是 TOP 系那套 32 位大写——所以这里只走 {@link PlatformSignatures#hmacSha256Hex} 的小写分支。</li>
 * </ul>
 * 令牌/密钥/签名值本身都是敏感串：本类只做纯计算，不写日志、不进异常消息（BR-01/BR-02）。
 *
 * @author kenyon
 */
public final class ShopeeSignature {

    private ShopeeSignature() {
    }

    /**
     * Shop 级待签串：{@code partner_id + apiPath + timestamp + access_token + shop_id}（§6，无分隔符）。
     * {@code accessToken}/{@code shopId} 为空按空串拼接而不是跳过——跳过等于换了拼接口径，签名必错。
     */
    public static String baseString(String partnerId, String apiPath, long timestamp, String accessToken, String shopId) {
        return nullToEmpty(partnerId) + nullToEmpty(apiPath) + timestamp
                + nullToEmpty(accessToken) + nullToEmpty(shopId);
    }

    /**
     * Public 级待签串：{@code partner_id + apiPath + timestamp}（§6：Public 级不拼 token）。
     * 只用于令牌交换——彼时还没有 {@code access_token} 可拼（见 {@link ShopeeTokenExchange}）。
     */
    public static String baseString(String partnerId, String apiPath, long timestamp) {
        return nullToEmpty(partnerId) + nullToEmpty(apiPath) + timestamp;
    }

    /**
     * 以 {@code partnerKey} 为密钥对 {@code baseString} 做 HMAC-SHA256，返回<b>小写</b> hex。
     */
    public static String sign(String partnerKey, String baseString) {
        return PlatformSignatures.hmacSha256Hex(partnerKey, baseString);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
