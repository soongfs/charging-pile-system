package com.bupt.charging.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PricingConfig {
    private Integer id;
    private BigDecimal peakPrice;
    private BigDecimal normalPrice;
    private BigDecimal valleyPrice;
    private BigDecimal serviceFeeRate;
    private LocalDateTime effectiveTime;
    private LocalDateTime updateTime;

    public PricingConfig() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public BigDecimal getPeakPrice() { return peakPrice; }
    public void setPeakPrice(BigDecimal peakPrice) { this.peakPrice = peakPrice; }
    public BigDecimal getNormalPrice() { return normalPrice; }
    public void setNormalPrice(BigDecimal normalPrice) { this.normalPrice = normalPrice; }
    public BigDecimal getValleyPrice() { return valleyPrice; }
    public void setValleyPrice(BigDecimal valleyPrice) { this.valleyPrice = valleyPrice; }
    public BigDecimal getServiceFeeRate() { return serviceFeeRate; }
    public void setServiceFeeRate(BigDecimal serviceFeeRate) { this.serviceFeeRate = serviceFeeRate; }
    public LocalDateTime getEffectiveTime() { return effectiveTime; }
    public void setEffectiveTime(LocalDateTime effectiveTime) { this.effectiveTime = effectiveTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
