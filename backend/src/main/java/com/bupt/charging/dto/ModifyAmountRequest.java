package com.bupt.charging.dto;

import java.math.BigDecimal;

public record ModifyAmountRequest(
        String carId,
        BigDecimal amount
) {}
