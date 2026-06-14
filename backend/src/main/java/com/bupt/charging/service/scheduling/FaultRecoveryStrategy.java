package com.bupt.charging.service.scheduling;

import com.bupt.charging.entity.ChargingPile;
import com.bupt.charging.entity.ChargingRequest;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 充电中故障恢复策略。
 *
 * <p>当充电桩在运行过程中发生故障时，对受影响车辆执行再调度。受影响车辆以最高优先级
 * 重新分配到同类型可用充电桩；同类型请求内部按原始请求时间（request_time）升序排列
 * （先到者优先）。若没有同类型可用桩，则车辆保持最高优先级进入对应模式等待队列，
 * 不跨类型分配。</p>
 */
@Component
public class FaultRecoveryStrategy implements SchedulingStrategy {
    @Override
    public ChargingRequest selectNextCar(List<ChargingRequest> waitingRequests) {
        if (waitingRequests == null || waitingRequests.isEmpty()) {
            return null;
        }
        return waitingRequests.stream()
                .min(Comparator.comparing(ChargingRequest::getRequestTime))
                .orElse(null);
    }

    @Override
    public ChargingPile selectPile(ChargingRequest request, List<ChargingPile> candidatePiles) {
        if (request == null || candidatePiles == null || candidatePiles.isEmpty()) {
            return null;
        }
        // 仅在同类型可用充电桩中选择，不跨类型占用充电桩。
        return candidatePiles.stream()
                .filter(pile -> pile.getType() == request.getRequestMode())
                .min(Comparator.comparing(ChargingPile::getId))
                .orElse(null);
    }
}
