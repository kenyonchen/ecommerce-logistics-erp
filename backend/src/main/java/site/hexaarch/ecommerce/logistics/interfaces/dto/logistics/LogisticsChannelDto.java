package site.hexaarch.ecommerce.logistics.interfaces.dto.logistics;

import lombok.Data;
import site.hexaarch.ecommerce.logistics.domain.logistics.entity.LogisticsChannel;

import java.math.BigDecimal;
import java.util.List;

/**
 * 物流渠道DTO，供前端渠道下拉与渠道管理使用。
 *
 * @author kenyon
 */
@Data
public class LogisticsChannelDto {
    private String channelId;
    private String channelCode;
    private String channelName;
    private String description;
    private List<String> countries;
    private BigDecimal basePrice;
    private BigDecimal pricePerGram;
    private String currency;
    private Boolean active;

    public static LogisticsChannelDto from(LogisticsChannel channel) {
        LogisticsChannelDto dto = new LogisticsChannelDto();
        dto.setChannelId(channel.getChannelId());
        dto.setChannelCode(channel.getChannelCode());
        dto.setChannelName(channel.getChannelName());
        dto.setDescription(channel.getDescription());
        dto.setCountries(channel.getCountries());
        dto.setBasePrice(channel.getBasePrice());
        dto.setPricePerGram(channel.getPricePerGram());
        dto.setCurrency(channel.getCurrency());
        dto.setActive(channel.isActive());
        return dto;
    }
}
