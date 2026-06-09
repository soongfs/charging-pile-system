package com.bupt.charging.dto;

public record ChargingCommandRequest(
        String carId,
        Integer pileId
) {}
