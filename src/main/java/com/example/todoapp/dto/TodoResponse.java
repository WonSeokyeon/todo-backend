package com.example.todoapp.dto;

import com.example.todoapp.domain.Priority;
import com.example.todoapp.domain.Todo;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 본인 데이터만 조회하므로 사용자 정보를 넣지 않는다 (넣으면 N+1 — CLAUDE.md 4장).
 */
public record TodoResponse(
        Long id,
        String title,
        String content,
        boolean completed,
        Priority priority,
        LocalDate dueDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static TodoResponse from(Todo todo) {
        return new TodoResponse(
                todo.getId(),
                todo.getTitle(),
                todo.getContent(),
                todo.isCompleted(),
                todo.getPriority(),
                todo.getDueDate(),
                todo.getCreatedAt(),
                todo.getUpdatedAt()
        );
    }
}
