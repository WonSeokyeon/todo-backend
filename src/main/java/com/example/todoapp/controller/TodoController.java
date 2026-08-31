package com.example.todoapp.controller;

import com.example.todoapp.domain.User;
import com.example.todoapp.dto.ApiResponse;
import com.example.todoapp.dto.PageResponse;
import com.example.todoapp.dto.ToggleRequest;
import com.example.todoapp.dto.TodoCreateRequest;
import com.example.todoapp.dto.TodoResponse;
import com.example.todoapp.dto.TodoUpdateRequest;
import com.example.todoapp.service.TodoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TodoResponse>>> list(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Boolean completed,
            @RequestParam(required = false) String keyword
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                todoService.list(user.getId(), page, size, sort, completed, keyword)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TodoResponse>> create(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody TodoCreateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(todoService.create(user, request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TodoResponse>> get(
            @AuthenticationPrincipal User user,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(todoService.get(user.getId(), id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TodoResponse>> update(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody TodoUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(todoService.update(user.getId(), id, request)));
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<ApiResponse<TodoResponse>> toggle(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody ToggleRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(todoService.toggle(user.getId(), id, request.completed())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal User user,
            @PathVariable Long id
    ) {
        todoService.delete(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
