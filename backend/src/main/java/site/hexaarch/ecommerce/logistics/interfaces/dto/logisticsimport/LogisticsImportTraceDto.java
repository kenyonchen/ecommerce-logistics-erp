package site.hexaarch.ecommerce.logistics.interfaces.dto.logisticsimport;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导入物流轨迹节点DTO
 *
 * @author kenyon
 */
@Data
@Schema(description = "物流轨迹节点")
public class LogisticsImportTraceDto {
    @Schema(description = "轨迹时间")
    private LocalDateTime time;
    @Schema(description = "轨迹对应的物流状态枚举名")
    private String status;
    @Schema(description = "轨迹描述")
    private String description;
    @Schema(description = "轨迹地点")
    private String location;
}
