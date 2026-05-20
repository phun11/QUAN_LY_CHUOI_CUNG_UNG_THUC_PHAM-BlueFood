package com.example.agritrace.controller;

import com.example.agritrace.dto.ApiResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    public ApiResponse<?> handle(Exception e) {
        return ApiResponse.fail(e.getMessage());
    }
}
