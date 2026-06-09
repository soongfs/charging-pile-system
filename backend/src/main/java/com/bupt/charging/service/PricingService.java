package com.bupt.charging.service;

import com.bupt.charging.dto.PricingUpdateRequest;
import com.bupt.charging.entity.PricingConfig;
import com.bupt.charging.mapper.PricingConfigMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class PricingService {
    private final PricingConfigMapper pricingMapper;

    public PricingService(PricingConfigMapper pricingMapper) {
        this.pricingMapper = pricingMapper;
    }

    public PricingConfig getCurrentConfig() {
        PricingConfig config = pricingMapper.getCurrent();
        if (config != null) {
            return config;
        }
        PricingConfig fallback = new PricingConfig();
        fallback.setId(1);
        fallback.setPeakPrice(BigDecimal.valueOf(1.2));
        fallback.setNormalPrice(BigDecimal.valueOf(0.8));
        fallback.setValleyPrice(BigDecimal.valueOf(0.6));
        fallback.setServiceFeeRate(BigDecimal.valueOf(0.2));
        LocalDateTime now = LocalDateTime.now();
        fallback.setEffectiveTime(now);
        fallback.setUpdateTime(now);
        pricingMapper.upsert(fallback);
        return fallback;
    }

    public int setParameters(PricingUpdateRequest request) {
        if (request == null
                || isNegative(request.peakPrice())
                || isNegative(request.normalPrice())
                || isNegative(request.valleyPrice())
                || isNegative(request.serviceFeeRate())) {
            return 1;
        }

        PricingConfig config = new PricingConfig();
        config.setId(1);
        config.setPeakPrice(request.peakPrice());
        config.setNormalPrice(request.normalPrice());
        config.setValleyPrice(request.valleyPrice());
        config.setServiceFeeRate(request.serviceFeeRate());
        LocalDateTime now = LocalDateTime.now();
        config.setEffectiveTime(now);
        config.setUpdateTime(now);
        int rows = pricingMapper.upsert(config);
        return rows > 0 ? 0 : 1;
    }

    private boolean isNegative(BigDecimal value) {
        return value == null || value.signum() < 0;
    }
}
