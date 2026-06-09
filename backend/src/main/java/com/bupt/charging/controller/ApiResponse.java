package com.bupt.charging.controller;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ApiResponse {
    private ApiResponse() {}

    public static Map<String, Object> success() {
        return success(Map.of());
    }

    public static Map<String, Object> success(Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 0);
        body.put("message", "success");
        body.put("data", data == null ? Map.of() : data);
        return body;
    }

    public static Map<String, Object> failure(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 1);
        body.put("message", message == null || message.isBlank() ? "failed" : message);
        body.put("data", Map.of());
        return body;
    }

    public static Map<String, Object> fromResult(int result, String failureMessage) {
        return result == 0 ? success() : failure(failureMessage);
    }
}
