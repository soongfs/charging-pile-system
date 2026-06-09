package com.bupt.charging.controller;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public Map<String, Object> handleBadRequest(IllegalArgumentException ex) {
        return ApiResponse.failure(ex.getMessage());
    }

    @ExceptionHandler({NullPointerException.class, ClassCastException.class})
    public Map<String, Object> handleInvalidPayload(RuntimeException ex) {
        return ApiResponse.failure("请求参数无效");
    }
}
