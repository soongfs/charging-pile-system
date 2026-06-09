package com.bupt.charging.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ChargingProgressResponse(
        Integer pileId,
        Long requestId,
        BigDecimal requestAmount,
        Integer requestMode,
        String modeLabel,
        BigDecimal chargedAmount,
        long elapsedSeconds,
        LocalDateTime startTime,
        BigDecimal chargeFee,
        BigDecimal serviceFee,
        BigDecimal totalFee,
        BigDecimal powerKw,
        BigDecimal progressPercent,
        boolean completed
) {}
