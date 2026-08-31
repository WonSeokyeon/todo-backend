package com.example.todoapp.dto;

import com.example.todoapp.domain.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record TodoCreateRequest(
        @NotBlank @Size(max = 200, message = "제목은 200자를 넘을 수 없습니다.") String title,
        @Size(max = 50000, message = "본문은 50,000자를 넘을 수 없습니다.") String content,
        Priority priority,
        LocalDate dueDate
) {
}
