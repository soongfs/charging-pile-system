package com.bupt.charging.entity;

import com.bupt.charging.enums.ChargingMode;
import com.bupt.charging.enums.RequestState;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ChargingRequest {
    private Long id;
    private String carId;
    private BigDecimal requestAmount;
    private ChargingMode requestMode;
    private LocalDateTime requestTime;
    private RequestState carState;
    private Integer pileId;
    private Integer queueNum;
    private Integer priority;
    private LocalDateTime updateTime;
    private Long version;

    public ChargingRequest() {}

    public ChargingRequest(String carId, BigDecimal requestAmount, ChargingMode requestMode) {
        this.carId = carId;
        this.requestAmount = requestAmount;
        this.requestMode = requestMode;
        this.requestTime = LocalDateTime.now();
        this.updateTime = this.requestTime;
        this.carState = RequestState.WAITING;
        this.priority = 0;
        this.version = 0L;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCarId() { return carId; }
    public void setCarId(String carId) { this.carId = carId; }
    public BigDecimal getRequestAmount() { return requestAmount; }
    public void setRequestAmount(BigDecimal requestAmount) { this.requestAmount = requestAmount; }
    public ChargingMode getRequestMode() { return requestMode; }
    public void setRequestMode(ChargingMode requestMode) { this.requestMode = requestMode; }
    public LocalDateTime getRequestTime() { return requestTime; }
    public void setRequestTime(LocalDateTime requestTime) { this.requestTime = requestTime; }
    public RequestState getCarState() { return carState; }
    public void setCarState(RequestState carState) { this.carState = carState; }
    public Integer getPileId() { return pileId; }
    public void setPileId(Integer pileId) { this.pileId = pileId; }
    public Integer getQueueNum() { return queueNum; }
    public void setQueueNum(Integer queueNum) { this.queueNum = queueNum; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public boolean isWaiting() {
        return carState == RequestState.WAITING;
    }

    public boolean isDispatched() {
        return carState == RequestState.DISPATCHED && pileId != null;
    }

    public boolean isCharging() {
        return carState == RequestState.CHARGING;
    }
}
