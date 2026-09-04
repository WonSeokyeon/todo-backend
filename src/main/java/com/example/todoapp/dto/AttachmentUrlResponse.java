package com.example.todoapp.dto;

/**
 * 조회 URL 일괄 발급 응답의 한 건.
 *
 * <p>본문에 이미지가 N개면 요청도 N번이 되는 것을 막기 위해 목록으로 주고받는다.
 * 요청한 id 중 타인 소유·삭제된 것은 응답에서 빠지므로, 프론트는 빠진 id를
 * "표시할 수 없는 이미지"로 처리한다.
 */
public record AttachmentUrlResponse(
        Long id,
        String viewUrl
) {
}
