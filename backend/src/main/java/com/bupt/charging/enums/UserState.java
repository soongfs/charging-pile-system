package com.bupt.charging.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum UserState {
    INACTIVE("inactive"),
    ACTIVE("active");

    private final String value;

    UserState(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static UserState fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (UserState state : values()) {
            if (state.value.equalsIgnoreCase(value.trim()) || state.name().equalsIgnoreCase(value.trim())) {
                return state;
            }
        }
        throw new IllegalArgumentException("未知用户状态: " + value);
    }
}
