package com.bupt.charging.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.math.BigDecimal;

public enum ChargingMode {
    SLOW(0, "slow", "慢充", BigDecimal.valueOf(60.0)),
    FAST(1, "fast", "快充", BigDecimal.valueOf(120.0));

    private final int code;
    private final String pileType;
    private final String label;
    private final BigDecimal defaultPowerKw;

    ChargingMode(int code, String pileType, String label, BigDecimal defaultPowerKw) {
        this.code = code;
        this.pileType = pileType;
        this.label = label;
        this.defaultPowerKw = defaultPowerKw;
    }

    public int getCode() {
        return code;
    }

    @JsonValue
    public String getPileType() {
        return pileType;
    }

    public String getLabel() {
        return label;
    }

    public BigDecimal getDefaultPowerKw() {
        return defaultPowerKw;
    }

    public static ChargingMode fromCode(int code) {
        for (ChargingMode mode : values()) {
            if (mode.code == code) {
                return mode;
            }
        }
        throw new IllegalArgumentException("充电模式必须为 0(慢充) 或 1(快充)");
    }

    public static ChargingMode fromValue(Object value) {
        if (value instanceof ChargingMode mode) {
            return mode;
        }
        if (value instanceof Number number) {
            return fromCode(number.intValue());
        }
        if (value == null) {
            throw new IllegalArgumentException("充电模式不能为空");
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("充电模式不能为空");
        }
        if ("0".equals(text) || "slow".equalsIgnoreCase(text) || "SLOW".equalsIgnoreCase(text)) {
            return SLOW;
        }
        if ("1".equals(text) || "fast".equalsIgnoreCase(text) || "FAST".equalsIgnoreCase(text)) {
            return FAST;
        }
        throw new IllegalArgumentException("充电模式必须为 0(慢充) 或 1(快充)");
    }
}
