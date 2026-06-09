package com.bupt.charging.dto;

public record ModifyModeRequest(
        String carId,
        Integer mode
) {}
