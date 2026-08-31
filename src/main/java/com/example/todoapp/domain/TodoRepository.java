package com.example.todoapp.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TodoRepository extends JpaRepository<Todo, Long> {

    Optional<Todo> findByIdAndDeletedAtIsNull(Long id);

    List<Todo> findByUserIdAndDeletedAtIsNull(Long userId);
}
