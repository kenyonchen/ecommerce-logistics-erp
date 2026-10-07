package site.hexaarch.ecommerce.logistics.domain.shop.valueobject;

/**
 * 店铺授权状态。
 * <p>
 * 站点粒度下同一卖家的多个站点各自持有 token，因此状态是**逐站点**的：
 * 某站点 {@link #EXPIRED} 而另一站点仍 {@link #ACTIVE} 是正常形态，界面必须分行展示而非聚合。
 * </p>
 *
 * @author kenyon
 */
public enum ShopAuthStatus {

    /**
     * 已建店、凭据尚未录入或未通过连通性测试
     */
    PENDING,

    /**
     * 凭据可用
     */
    ACTIVE,

    /**
     * 外呼失败，需人工介入
     */
    ERROR,

    /**
     * 令牌已过期，需重新授权
     */
    EXPIRED
}
