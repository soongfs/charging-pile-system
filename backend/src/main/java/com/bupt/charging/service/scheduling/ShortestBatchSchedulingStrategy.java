package com.bupt.charging.service.scheduling;

import com.bupt.charging.entity.ChargingPile;
import com.bupt.charging.entity.ChargingRequest;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class ShortestBatchSchedulingStrategy implements SchedulingStrategy {
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
        return candidatePiles.stream()
                .filter(pile -> pile.getType() == request.getRequestMode())
                .min(Comparator.comparing(pile -> request.getRequestAmount()
                        .divide(pile.getPowerKw(), 6, java.math.RoundingMode.HALF_UP)))
                .orElse(null);
    }
}
