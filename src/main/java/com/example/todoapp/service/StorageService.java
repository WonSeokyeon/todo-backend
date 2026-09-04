package com.example.todoapp.service;

import com.example.todoapp.domain.StorageType;

/**
 * 첨부 파일 저장소 추상화. 구현체는 app.storage.type에 따라 하나만 등록된다.
 *
 * <p>AttachmentService는 이 인터페이스에만 의존하고 어떤 구현체인지 알지 못한다.
 * Phase 13에서 S3StorageService로 갈아끼울 때 서비스·컨트롤러·프론트 코드가
 * 바뀌지 않아야 한다 — 바뀐다면 추상화가 잘못된 것이다.
 *
 * <p>업로드·조회 URL은 서버가 완성된 절대 URL로 내려준다. 프론트가 경로를 조립하지 않는다.
 */
public interface StorageService {

    StorageType getType();

    /**
     * 업로드용 URL을 발급한다.
     * 로컬은 서명 토큰이 붙은 백엔드 엔드포인트, S3는 presigned PUT URL이다.
     */
    String createUploadUrl(Long attachmentId, String storageKey, String contentType);

    /**
     * 조회용 URL을 발급한다.
     * 로컬은 서명 토큰이 붙은 백엔드 엔드포인트, S3는 presigned GET URL이다.
     */
    String createViewUrl(Long attachmentId, String storageKey);

    /**
     * 업로드가 실제로 끝났는지 확인하고 저장된 크기를 돌려준다.
     *
     * @throws com.example.todoapp.exception.BusinessException 파일이 없으면 409
     */
    long verifyUploaded(String storageKey);

    /** 물리 파일을 삭제한다. 이미 없으면 조용히 넘어간다(정리 배치가 재실행돼도 안전하도록). */
    void delete(String storageKey);
}
