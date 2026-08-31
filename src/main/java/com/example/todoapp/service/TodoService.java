package com.example.todoapp.service;

import com.example.todoapp.domain.Todo;
import com.example.todoapp.domain.TodoRepository;
import com.example.todoapp.domain.User;
import com.example.todoapp.dto.PageResponse;
import com.example.todoapp.dto.TodoCreateRequest;
import com.example.todoapp.dto.TodoResponse;
import com.example.todoapp.dto.TodoUpdateRequest;
import com.example.todoapp.exception.BusinessException;
import com.example.todoapp.exception.ErrorCode;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TodoService {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("createdAt", "dueDate");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final TodoRepository todoRepository;
    private final HtmlSanitizer htmlSanitizer;

    @Transactional
    public TodoResponse create(User user, TodoCreateRequest request) {
        Todo todo = Todo.create(
                user,
                request.title(),
                htmlSanitizer.sanitize(request.content()),
                request.priority(),
                request.dueDate()
        );
        return TodoResponse.from(todoRepository.save(todo));
    }

    @Transactional(readOnly = true)
    public PageResponse<TodoResponse> list(Long userId, int page, int size, String sortParam, Boolean completed, String keyword) {
        Pageable pageable = PageRequest.of(page, size, resolveSort(sortParam));
        return PageResponse.from(todoRepository.search(userId, completed, keyword, pageable), TodoResponse::from);
    }

    @Transactional(readOnly = true)
    public TodoResponse get(Long userId, Long id) {
        return TodoResponse.from(getOwned(userId, id));
    }

    /** PUT 전체 교체 — completed는 여기서 건드리지 않는다(CLAUDE.md 5장). */
    @Transactional
    public TodoResponse update(Long userId, Long id, TodoUpdateRequest request) {
        Todo todo = getOwned(userId, id);
        todo.updateContent(
                request.title(),
                htmlSanitizer.sanitize(request.content()),
                request.priority(),
                request.dueDate()
        );
        // @LastModifiedDate는 @PreUpdate(flush 시점)에 채워진다. flush 없이 바로 응답을
        // 만들면 updatedAt이 갱신 전 값으로 직렬화된다(DB에는 커밋 시 정상 반영되지만
        // 이 응답만 stale — 실측 확인).
        todoRepository.flush();
        return TodoResponse.from(todo);
    }

    /** 목표 상태를 그대로 반영한다(서버가 뒤집지 않는 멱등 설계). */
    @Transactional
    public TodoResponse toggle(Long userId, Long id, boolean completed) {
        Todo todo = getOwned(userId, id);
        todo.toggleComplete(completed);
        todoRepository.flush();
        return TodoResponse.from(todo);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        getOwned(userId, id).softDelete();
    }

    /** 존재하지 않는 id와 소유권 불일치를 구분하지 않고 동일하게 404로 응답한다(존재 여부 비노출). */
    private Todo getOwned(Long userId, Long id) {
        Todo todo = todoRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));
        if (!todo.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }
        return todo;
    }

    /** 화이트리스트 밖 정렬 값은 기본값으로 대체한다(없는 프로퍼티로 500 방지 — CLAUDE.md 5장). */
    private Sort resolveSort(String sortParam) {
        if (sortParam == null) {
            return DEFAULT_SORT;
        }
        String[] parts = sortParam.split(",");
        if (parts.length != 2 || !ALLOWED_SORT_PROPERTIES.contains(parts[0])) {
            return DEFAULT_SORT;
        }
        Sort.Direction direction = "asc".equalsIgnoreCase(parts[1]) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, parts[0]);
    }
}
