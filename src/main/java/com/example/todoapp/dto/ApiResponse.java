package com.example.todoapp.dto;

import com.example.todoapp.exception.ErrorCode;

/**
 * 모든 REST 응답이 따르는 공통 포맷 (CLAUDE.md 5장). OAuth2 콜백의 302 리다이렉트는 예외.
 */
public record ApiResponse<T>(boolean success, T data, ErrorResponse error) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode code, String message) {
        return new ApiResponse<>(false, null, new ErrorResponse(code.name(), message));
    }

    public record ErrorResponse(String code, String message) {
    }
}
