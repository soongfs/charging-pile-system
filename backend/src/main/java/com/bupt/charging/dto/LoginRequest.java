package com.bupt.charging.dto;

public record LoginRequest(
        String carId,
        String password
) {}
