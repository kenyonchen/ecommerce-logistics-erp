package site.hexaarch.ecommerce.logistics.interfaces.dto.logistics;

import lombok.Data;
import site.hexaarch.ecommerce.logistics.domain.logistics.valueobject.LogisticsTrace;

import java.time.format.DateTimeFormatter;

/**
 * 物流轨迹节点DTO。
 *
 * @author kenyon
 */
@Data
public class LogisticsTraceDto {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String time;
    private String status;
    private String statusLabel;
    private String description;
    private String location;

    public static LogisticsTraceDto from(LogisticsTrace trace) {
        LogisticsTraceDto dto = new LogisticsTraceDto();
        dto.setTime(trace.time() == null ? null : trace.time().format(FORMATTER));
        dto.setStatus(trace.status());
        dto.setDescription(trace.description());
        dto.setLocation(trace.location());
        if (trace.status() != null) {
            try {
                dto.setStatusLabel(site.hexaarch.ecommerce.logistics.domain.logistics.valueobject
                        .LogisticsStatus.valueOf(trace.status()).getDescription());
            } catch (IllegalArgumentException ignored) {
                dto.setStatusLabel(trace.status());
            }
        }
        return dto;
    }
}
