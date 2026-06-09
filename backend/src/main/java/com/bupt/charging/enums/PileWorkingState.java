package com.bupt.charging.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PileWorkingState {
    OFF("off"),
    IDLE("idle"),
    RUNNING("running"),
    CHARGING("charging"),
    FAULT("fault");

    private final String value;

    PileWorkingState(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static PileWorkingState fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (PileWorkingState state : values()) {
            if (state.value.equalsIgnoreCase(value.trim()) || state.name().equalsIgnoreCase(value.trim())) {
                return state;
            }
        }
        throw new IllegalArgumentException("未知充电桩工作状态: " + value);
    }
}
