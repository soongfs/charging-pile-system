package com.bupt.charging.entity;

import com.bupt.charging.enums.UserState;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class User {
    private String carId;
    private String userName;
    private BigDecimal carCapacity;
    private String password;
    private UserState state;
    private LocalDateTime createTime;

    public User() {}

    public User(String carId, String userName, BigDecimal carCapacity) {
        this.carId = carId;
        this.userName = userName;
        this.carCapacity = carCapacity;
        this.state = UserState.INACTIVE;
        this.createTime = LocalDateTime.now();
    }

    public String getCarId() { return carId; }
    public void setCarId(String carId) { this.carId = carId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public BigDecimal getCarCapacity() { return carCapacity; }
    public void setCarCapacity(BigDecimal carCapacity) { this.carCapacity = carCapacity; }
    @JsonIgnore
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public UserState getState() { return state; }
    public void setState(UserState state) { this.state = state; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
