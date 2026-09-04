package com.example.todoapp.dto;

/**
 * 업로드 URL 발급 응답.
 *
 * <p>uploadUrl은 서버가 완성한 절대 URL이다. 프론트는 이 URL이 로컬 백엔드를 가리키는지
 * S3를 가리키는지 몰라도 되며, 헤더 없이 그대로 PUT을 보낸다 (CLAUDE.md 5장).
 */
public record AttachmentPresignResponse(
        Long attachmentId,
        String uploadUrl,
        String storageKey
) {
}
