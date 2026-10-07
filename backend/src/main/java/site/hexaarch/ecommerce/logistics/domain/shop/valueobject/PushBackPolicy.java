package site.hexaarch.ecommerce.logistics.domain.shop.valueobject;

/**
 * 店铺级发货回写策略。
 * <p>
 * 这是「店铺的属性」，与物流单上的 {@code FulfillmentPushStatus}（「这一单推没推」）分属两层：
 * 合成一列会让改开关等于重写历史语义，事后查不出当时到底推没推。
 * </p>
 *
 * @author kenyon
 */
public enum PushBackPolicy {

    /**
     * 发货即自动推送平台
     */
    AUTO,

    /**
     * 人工点推
     */
    MANUAL,

    /**
     * 不推
     */
    OFF
}
