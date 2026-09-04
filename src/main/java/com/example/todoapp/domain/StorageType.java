package com.example.todoapp.domain;

/**
 * 첨부 파일이 실제로 저장된 스토리지 종류.
 *
 * <p>런타임 분기용이 아니다 — StorageService 구현체는 @ConditionalOnProperty로 하나만 등록되므로
 * 다른 타입의 행을 처리할 빈이 애초에 존재하지 않는다. 감사·향후 마이그레이션 기록 용도이며,
 * 현재 활성 타입과 다른 행에 접근하면 조용히 실패하지 말고 명시적 예외를 던진다 (CLAUDE.md 4장).
 */
public enum StorageType {
    LOCAL,
    S3
}
