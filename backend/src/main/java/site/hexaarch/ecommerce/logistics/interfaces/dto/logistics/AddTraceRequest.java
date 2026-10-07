package site.hexaarch.ecommerce.logistics.interfaces.dto.logistics;

import lombok.Data;

/**
 * 手工登记物流轨迹请求。
 *
 * @author kenyon
 */
@Data
public class AddTraceRequest {
    /**
     * 轨迹时间（yyyy-MM-dd HH:mm:ss），为空时取当前时间
     */
    private String time;
    /**
     * 轨迹对应的物流状态枚举名，可为空
     */
    private String status;
    private String description;
    private String location;
}
