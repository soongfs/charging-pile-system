package com.bupt.charging.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ChargingRecord {
    private Long id;
    private String carId;
    private Long requestId;
    private Integer pileId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal chargeAmount;
    private BigDecimal chargeFee;
    private BigDecimal serviceFee;

    public ChargingRecord() {}

    public ChargingRecord(String carId, Long requestId, Integer pileId) {
        this.carId = carId;
        this.requestId = requestId;
        this.pileId = pileId;
        this.chargeAmount = BigDecimal.ZERO;
        this.chargeFee = BigDecimal.ZERO;
        this.serviceFee = BigDecimal.ZERO;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCarId() { return carId; }
    public void setCarId(String carId) { this.carId = carId; }
    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }
    public Integer getPileId() { return pileId; }
    public void setPileId(Integer pileId) { this.pileId = pileId; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public BigDecimal getChargeAmount() { return chargeAmount; }
    public void setChargeAmount(BigDecimal chargeAmount) { this.chargeAmount = chargeAmount; }
    public BigDecimal getChargeFee() { return chargeFee; }
    public void setChargeFee(BigDecimal chargeFee) { this.chargeFee = chargeFee; }
    public BigDecimal getServiceFee() { return serviceFee; }
    public void setServiceFee(BigDecimal serviceFee) { this.serviceFee = serviceFee; }
}
