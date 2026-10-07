package site.hexaarch.ecommerce.logistics.domain.logistics.valueobject;

import java.time.LocalDateTime;

/**
 * 物流轨迹节点值对象。
 *
 * @param time        轨迹发生时间
 * @param status      该节点对应的物流状态名称，可为空
 * @param description 轨迹描述
 * @param location    发生地点
 * @author kenyon
 */
public record LogisticsTrace(LocalDateTime time, String status, String description, String location) {

    public static LogisticsTrace of(LocalDateTime time, String status, String description, String location) {
        return new LogisticsTrace(time == null ? LocalDateTime.now() : time, status, description, location);
    }
}
