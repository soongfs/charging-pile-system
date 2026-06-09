package com.bupt.charging.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PilePowerState {
    ON("on"),
    OFF("off");

    private final String value;

    PilePowerState(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static PilePowerState fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (PilePowerState state : values()) {
            if (state.value.equalsIgnoreCase(value.trim()) || state.name().equalsIgnoreCase(value.trim())) {
                return state;
            }
        }
        throw new IllegalArgumentException("未知充电桩电源状态: " + value);
    }
}
