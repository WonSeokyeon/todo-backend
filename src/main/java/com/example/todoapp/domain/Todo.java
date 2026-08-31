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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * todos 테이블 매핑 엔티티. user는 반드시 LAZY로 가져온다(기본값 EAGER 금지 — CLAUDE.md 4장).
 * completed는 오직 toggleComplete()로만, 나머지 필드는 updateContent()(PUT 전체 교체)로만 바꾼다.
 */
@Getter
@Entity
@Table(name = "todos", indexes = @Index(name = "idx_todos_user_deleted", columnList = "user_id, deleted_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Todo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private boolean completed = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority = Priority.MEDIUM;

    private LocalDate dueDate;

    private LocalDateTime deletedAt;

    private Todo(User user, String title, String content, Priority priority, LocalDate dueDate) {
        this.user = user;
        this.title = title;
        this.content = content;
        this.priority = priority != null ? priority : Priority.MEDIUM;
        this.dueDate = dueDate;
    }

    public static Todo create(User user, String title, String content, Priority priority, LocalDate dueDate) {
        return new Todo(user, title, content, priority, dueDate);
    }

    /** PUT 전체 교체 — completed는 여기서 건드리지 않는다 (CLAUDE.md 5장 PUT·toggle 역할 분리). */
    public void updateContent(String title, String content, Priority priority, LocalDate dueDate) {
        this.title = title;
        this.content = content;
        this.priority = priority != null ? priority : Priority.MEDIUM;
        this.dueDate = dueDate;
    }

    /** PATCH /toggle — 목표 상태를 그대로 반영한다(서버가 뒤집지 않는 멱등 설계). */
    public void toggleComplete(boolean completed) {
        this.completed = completed;
    }

    public void softDelete() {
        // LocalDateTime.now()는 JVM 기본 타임존(KST) 벽시계 값이라 UTC 저장 규칙을 어긴다.
        this.deletedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
