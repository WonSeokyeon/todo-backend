package com.example.todoapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 업로드 URL 발급 요청.
 *
 * <p>fileSize는 클라이언트가 신고한 값이라 그대로 믿지 않는다. 여기서는 명백히 큰 요청을
 * 미리 거르는 용도로만 쓰고, 실제 크기는 업로드 중 스트림에서 다시 센다.
 */
public record AttachmentPresignRequest(

        @NotBlank(message = "파일명은 필수입니다.")
        @Size(max = 255, message = "파일명이 너무 깁니다.")
        String filename,

        @NotBlank(message = "Content-Type은 필수입니다.")
        @Size(max = 100, message = "Content-Type이 너무 깁니다.")
        String contentType,

        @NotNull(message = "파일 크기는 필수입니다.")
        @Positive(message = "파일 크기가 올바르지 않습니다.")
        Long fileSize
) {
}
