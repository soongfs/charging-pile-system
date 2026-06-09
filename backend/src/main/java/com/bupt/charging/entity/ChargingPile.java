package com.bupt.charging.entity;

import com.bupt.charging.enums.ChargingMode;
import com.bupt.charging.enums.PilePowerState;
import com.bupt.charging.enums.PileWorkingState;

import java.math.BigDecimal;

public class ChargingPile {
    private Integer id;
    private ChargingMode type;
    private PilePowerState powerState;
    private PileWorkingState workingState;
    private BigDecimal powerKw;
    private int totalChargeNum;
    private int totalChargeTime;
    private BigDecimal totalCapacity;

    public ChargingPile() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public ChargingMode getType() { return type; }
    public void setType(ChargingMode type) { this.type = type; }
    public PilePowerState getPowerState() { return powerState; }
    public void setPowerState(PilePowerState powerState) { this.powerState = powerState; }
    public PileWorkingState getWorkingState() { return workingState; }
    public void setWorkingState(PileWorkingState workingState) { this.workingState = workingState; }
    public BigDecimal getPowerKw() { return powerKw; }
    public void setPowerKw(BigDecimal powerKw) { this.powerKw = powerKw; }
    public int getTotalChargeNum() { return totalChargeNum; }
    public void setTotalChargeNum(int totalChargeNum) { this.totalChargeNum = totalChargeNum; }
    public int getTotalChargeTime() { return totalChargeTime; }
    public void setTotalChargeTime(int totalChargeTime) { this.totalChargeTime = totalChargeTime; }
    public BigDecimal getTotalCapacity() { return totalCapacity; }
    public void setTotalCapacity(BigDecimal totalCapacity) { this.totalCapacity = totalCapacity; }
}
