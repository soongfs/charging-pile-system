package com.bupt.charging.service.scheduling;

import com.bupt.charging.entity.ChargingPile;
import com.bupt.charging.entity.ChargingRequest;

import java.util.List;

public interface SchedulingStrategy {
    ChargingRequest selectNextCar(List<ChargingRequest> waitingRequests);

    ChargingPile selectPile(ChargingRequest request, List<ChargingPile> candidatePiles);
}
