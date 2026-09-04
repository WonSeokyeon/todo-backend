package com.example.todoapp.dto;

import com.example.todoapp.domain.Attachment;

/** 업로드 확정 응답. viewUrl은 만료되므로 본문 HTML에 저장하지 않는다 (CLAUDE.md 6장). */
public record AttachmentResponse(
        Long id,
        String originalFilename,
        String contentType,
        long fileSize,
        String viewUrl
) {
    public static AttachmentResponse of(Attachment attachment, String viewUrl) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getFileSize(),
                viewUrl
        );
    }
}
