package com.bupt.charging.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Bill {
    private Long id;
    private Long recordId;
    private Long requestId;
    private String carId;
    private LocalDate date;
    private Integer pileId;
    private BigDecimal chargeAmount;
    private int chargeDuration;
    private BigDecimal totalChargeFee;
    private BigDecimal totalServiceFee;
    private BigDecimal totalFee;

    public Bill() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }
    public String getCarId() { return carId; }
    public void setCarId(String carId) { this.carId = carId; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public Integer getPileId() { return pileId; }
    public void setPileId(Integer pileId) { this.pileId = pileId; }
    public BigDecimal getChargeAmount() { return chargeAmount; }
    public void setChargeAmount(BigDecimal chargeAmount) { this.chargeAmount = chargeAmount; }
    public int getChargeDuration() { return chargeDuration; }
    public void setChargeDuration(int chargeDuration) { this.chargeDuration = chargeDuration; }
    public BigDecimal getTotalChargeFee() { return totalChargeFee; }
    public void setTotalChargeFee(BigDecimal totalChargeFee) { this.totalChargeFee = totalChargeFee; }
    public BigDecimal getTotalServiceFee() { return totalServiceFee; }
    public void setTotalServiceFee(BigDecimal totalServiceFee) { this.totalServiceFee = totalServiceFee; }
    public BigDecimal getTotalFee() { return totalFee; }
    public void setTotalFee(BigDecimal totalFee) { this.totalFee = totalFee; }
}
