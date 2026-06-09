package com.bupt.charging.service;

import com.bupt.charging.entity.ChargingPile;
import com.bupt.charging.entity.ChargingRequest;
import com.bupt.charging.enums.ChargingMode;
import com.bupt.charging.enums.PilePowerState;
import com.bupt.charging.enums.PileWorkingState;
import com.bupt.charging.enums.RequestState;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class ChargingStateMachine {
    private static final Map<RequestState, Set<RequestState>> REQUEST_TRANSITIONS = Map.of(
            RequestState.WAITING, EnumSet.of(RequestState.DISPATCHED, RequestState.CANCELLED),
            RequestState.DISPATCHED, EnumSet.of(RequestState.WAITING, RequestState.CHARGING, RequestState.CANCELLED),
            RequestState.CHARGING, EnumSet.of(RequestState.DONE),
            RequestState.DONE, EnumSet.noneOf(RequestState.class),
            RequestState.CANCELLED, EnumSet.noneOf(RequestState.class)
    );

    public boolean canTransition(RequestState current, RequestState target) {
        return current != null && target != null
                && REQUEST_TRANSITIONS.getOrDefault(current, Set.of()).contains(target);
    }

    public void applyRequestState(ChargingRequest request, RequestState target) {
        if (request == null || !canTransition(request.getCarState(), target)) {
            throw new IllegalStateException("充电请求状态流转无效");
        }
        request.setCarState(target);
    }

    public boolean canStartPile(ChargingPile pile) {
        return pile != null
                && pile.getPowerState() == PilePowerState.ON
                && pile.getWorkingState() != PileWorkingState.FAULT
                && pile.getWorkingState() != PileWorkingState.CHARGING;
    }

    public boolean canPowerOff(ChargingPile pile) {
        return pile != null && pile.getWorkingState() != PileWorkingState.CHARGING;
    }

    public boolean canAcceptCharging(ChargingPile pile, ChargingMode mode) {
        return pile != null
                && pile.getPowerState() == PilePowerState.ON
                && (pile.getWorkingState() == PileWorkingState.IDLE || pile.getWorkingState() == PileWorkingState.RUNNING)
                && pile.getType() == mode;
    }
}
