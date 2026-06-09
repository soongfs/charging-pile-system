package com.bupt.charging.dto;

import com.bupt.charging.entity.ChargingRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ChargingStateResponse(
        Long requestId,
        int carPosition,
        int carNumberBeforePosition,
        String carState,
        int queueNum,
        int requestMode,
        String modeLabel,
        BigDecimal requestAmount,
        Integer pileId,
        LocalDateTime requestTime,
        boolean canStart
) {
    public static ChargingStateResponse from(ChargingRequest request, int carsAhead) {
        int queueNumber = request.getQueueNum() == null ? 0 : request.getQueueNum();
        return new ChargingStateResponse(
                request.getId(),
                queueNumber,
                carsAhead,
                request.getCarState().getValue(),
                queueNumber,
                request.getRequestMode().getCode(),
                request.getRequestMode().getLabel(),
                request.getRequestAmount(),
                request.getPileId(),
                request.getRequestTime(),
                request.isDispatched()
        );
    }
}
