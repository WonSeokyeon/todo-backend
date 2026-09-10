package com.example.todoapp.exception;

import com.example.todoapp.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 모든 예외 응답은 이 한 곳에서만 만든다 (부모 CLAUDE.md 절대규칙 6).
 * 컨트롤러에서 개별 try-catch로 응답을 조립하지 않는다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        return ResponseEntity.status(e.getErrorCode().getStatus())
                .body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ErrorCode.INVALID_INPUT.getDefaultMessage());
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT, message));
    }

    // JSON 파싱 실패(잘못된 enum 값, 형식이 깨진 바디 등)가 500으로 새지 않도록 막는다.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getDefaultMessage()));
    }

    /**
     * 요청 파라미터 바인딩 실패를 400으로 내린다 (CLAUDE.md 11장).
     *
     * <p>둘 다 catch-all에 걸리면 500 INTERNAL_ERROR가 나가는데, 원인은 서버가 아니라
     * 잘못된 요청이다. 프론트는 500을 "서버 오류" 문구와 재시도 버튼으로 처리하므로
     * 사용자가 원인을 알 수 없다.
     * <ul>
     *   <li>Missing: 필수 쿼리 파라미터 누락 — 예: /attachments/{id}/raw 에 token이 없을 때</li>
     *   <li>TypeMismatch: 경로·쿼리 값의 타입 불일치 — 예: /todos/abc (Long 자리에 문자열)</li>
     * </ul>
     */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequestBinding(Exception e) {
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getDefaultMessage()));
    }

    // BCryptPasswordEncoder가 72바이트 초과 시 던지는 예외 등 안전장치 (CLAUDE.md 4장)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT, e.getMessage()));
    }

    // 5xx는 원인 추적이 목적이므로 스택 트레이스를 포함해 error로 남긴다 (CLAUDE.md 11장).
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("처리되지 않은 예외가 발생했습니다.", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage()));
    }
}
