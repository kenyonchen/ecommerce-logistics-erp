package site.hexaarch.ecommerce.logistics.domain.logistics.entity;

import lombok.Builder;
import lombok.Getter;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.PriceRule;

import java.util.List;
import java.util.UUID;

/**
 * 物流渠道实体，物流服务提供商，如FedEx、UPS等。
 *
 * @author kenyon
 */
@Getter
@Builder(toBuilder = true)
public class LogisticsChannel {
    private final String channelId;
    private final String channelName;
    private final String description;
    private final List<String> countries;
    private final List<PriceRule> priceRules;
    private final boolean active;

    /**
     * 构造函数，确保所有必填字段都不为空。
     *
     * @param channelId   渠道ID
     * @param channelName 渠道名称
     * @param description 渠道描述
     * @param countries   支持的国家列表
     * @param priceRules  价格规则列表
     * @param active      是否激活
     */
    public LogisticsChannel(String channelId, String channelName, String description, List<String> countries, List<PriceRule> priceRules, boolean active) {
        this.channelId = channelId != null ? channelId : UUID.randomUUID().toString();
        this.channelName = channelName;
        this.description = description;
        this.countries = countries != null ? countries : List.of();
        this.priceRules = priceRules != null ? priceRules : List.of();
        this.active = active;
    }

    /**
     * 检查渠道是否支持指定国家。
     *
     * @param country 国家代码
     * @return 如果支持则返回true，否则返回false
     */
    public boolean supportsCountry(String country) {
        return this.countries.contains(country);
    }

    /**
     * 激活渠道。
     *
     * @return 激活后的渠道
     */
    public LogisticsChannel activate() {
        return new LogisticsChannel(
                this.channelId,
                this.channelName,
                this.description,
                this.countries,
                this.priceRules,
                true
        );
    }

    /**
     * 停用渠道。
     *
     * @return 停用后的渠道
     */
    public LogisticsChannel deactivate() {
        return new LogisticsChannel(
                this.channelId,
                this.channelName,
                this.description,
                this.countries,
                this.priceRules,
                false
        );
    }

    /**
     * 获取渠道ID
     */
    public String getChannelId() {
        return channelId;
    }

    /**
     * 获取价格规则列表
     */
    public List<PriceRule> getPriceRules() {
        return priceRules;
    }
}