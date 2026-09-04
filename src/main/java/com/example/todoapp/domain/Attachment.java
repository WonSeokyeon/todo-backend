package com.example.todoapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * attachments 테이블 매핑 엔티티 (CLAUDE.md 4장).
 *
 * <p>user·todo 모두 반드시 LAZY로 가져온다(@ManyToOne 기본값 EAGER 금지).
 * todo는 NULL을 허용한다 — 업로드 시점에는 할 일이 아직 저장되지 않았기 때문이다.
 *
 * <p>Setter를 열지 않는다. 상태 변경은 complete()·link()·softDelete()로만 한다.
 */
@Getter
@Entity
@Table(
        name = "attachments",
        indexes = {
                @Index(name = "idx_attachments_todo", columnList = "todo_id"),
                @Index(name = "idx_attachments_status_created", columnList = "status, created_at"),
                @Index(name = "idx_attachments_user", columnList = "user_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attachment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 업로더. 소유권 검증의 기준이다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 연결된 할 일. TEMP 상태에서는 null이다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "todo_id")
    private Todo todo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StorageType storageType;

    /** 로컬 상대경로 또는 S3 객체 키. todos/{userId}/{yyyy}/{MM}/{uuid}.{ext} */
    @Column(nullable = false, length = 512, unique = true)
    private String storageKey;

    @Column(nullable = false, length = 255)
    private String originalFilename;

    @Column(nullable = false, length = 100)
    private String contentType;

    /** presign 시에는 클라이언트가 신고한 값, complete 시 실측값으로 갱신된다. */
    @Column(nullable = false)
    private long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttachmentStatus status;

    private LocalDateTime deletedAt;

    private Attachment(User user, StorageType storageType, String storageKey,
                       String originalFilename, String contentType, long fileSize) {
        this.user = user;
        this.storageType = storageType;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.status = AttachmentStatus.TEMP;
    }

    /** presign 단계에서 만드는 임시 행. 아직 파일 실체가 없다. */
    public static Attachment createTemp(User user, StorageType storageType, String storageKey,
                                        String originalFilename, String contentType, long fileSize) {
        return new Attachment(user, storageType, storageKey, originalFilename, contentType, fileSize);
    }

    /** complete 단계 — 실제로 저장된 크기로 갱신한다. 상태는 아직 TEMP다(할 일 저장 시 LINKED가 된다). */
    public void completeUpload(long actualSize) {
        this.fileSize = actualSize;
    }

    /** 할 일 저장 시 본문에 남아 있는 첨부를 연결한다. */
    public void link(Todo todo) {
        this.todo = todo;
        this.status = AttachmentStatus.LINKED;
    }

    public void softDelete() {
        // LocalDateTime.now()는 JVM 기본 타임존(KST) 벽시계 값이라 UTC 저장 규칙을 어긴다.
        this.deletedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public boolean isOwnedBy(Long userId) {
        return this.user.getId().equals(userId);
    }
}
