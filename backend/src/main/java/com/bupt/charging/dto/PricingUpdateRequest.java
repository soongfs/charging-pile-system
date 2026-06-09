package com.bupt.charging.dto;

import java.math.BigDecimal;

public record PricingUpdateRequest(
        BigDecimal peakPrice,
        BigDecimal normalPrice,
        BigDecimal valleyPrice,
        BigDecimal serviceFeeRate
) {}
