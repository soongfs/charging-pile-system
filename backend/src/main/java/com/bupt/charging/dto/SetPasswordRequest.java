package com.bupt.charging.dto;

public record SetPasswordRequest(
        String carId,
        String password
) {}
