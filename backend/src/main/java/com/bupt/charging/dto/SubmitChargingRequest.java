package com.bupt.charging.dto;

import java.math.BigDecimal;

public record SubmitChargingRequest(
        String carId,
        BigDecimal requestAmount,
        Integer requestMode
) {}
