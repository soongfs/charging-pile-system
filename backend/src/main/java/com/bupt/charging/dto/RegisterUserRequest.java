package com.bupt.charging.dto;

import java.math.BigDecimal;

public record RegisterUserRequest(
        String carId,
        String userName,
        BigDecimal carCapacity
) {}
