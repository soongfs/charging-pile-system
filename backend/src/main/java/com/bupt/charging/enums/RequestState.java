package com.bupt.charging.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RequestState {
    WAITING("waiting"),
    DISPATCHED("dispatched"),
    CHARGING("charging"),
    DONE("done"),
    CANCELLED("cancelled");

    private final String value;

    RequestState(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static RequestState fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (RequestState state : values()) {
            if (state.value.equalsIgnoreCase(value.trim()) || state.name().equalsIgnoreCase(value.trim())) {
                return state;
            }
        }
        throw new IllegalArgumentException("未知充电请求状态: " + value);
    }
}
