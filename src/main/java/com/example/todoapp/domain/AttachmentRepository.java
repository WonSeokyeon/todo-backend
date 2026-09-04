package com.example.todoapp.domain;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    Optional<Attachment> findByIdAndDeletedAtIsNull(Long id);

    /** 조회 URL 일괄 발급·본문 연결 처리에서 쓴다. 한 번에 가져와 N+1을 피한다. */
    List<Attachment> findByIdInAndDeletedAtIsNull(Collection<Long> ids);

    /** 할 일 저장 시 "본문에서 사라진 첨부"를 찾기 위해 현재 연결된 목록을 가져온다. */
    List<Attachment> findByTodoIdAndDeletedAtIsNull(Long todoId);

    /** 정리 배치 — 업로드하다 만 고아 파일 (TEMP + 24시간 경과). */
    List<Attachment> findByStatusAndDeletedAtIsNullAndCreatedAtBefore(AttachmentStatus status, LocalDateTime threshold);

    /** 정리 배치 — soft delete 후 유예 기간이 지난 것. 행은 남기고 파일만 지운다. */
    List<Attachment> findByDeletedAtNotNullAndDeletedAtBefore(LocalDateTime threshold);
}
