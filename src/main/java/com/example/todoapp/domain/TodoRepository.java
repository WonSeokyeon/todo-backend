package com.example.todoapp.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TodoRepository extends JpaRepository<Todo, Long> {

    Optional<Todo> findByIdAndDeletedAtIsNull(Long id);

    List<Todo> findByUserIdAndDeletedAtIsNull(Long userId);

    // completed 미지정(null) 시 전체 반환, keyword 미지정(null) 시 제목 필터 없이 반환 (CLAUDE.md 5장)
    // CAST(:keyword AS string)이 없으면 keyword가 null일 때 PostgreSQL이 파라미터 타입을
    // 추론하지 못해 "lower(bytea) 이름의 함수가 없음" 오류가 난다(실측 확인).
    @Query("SELECT t FROM Todo t WHERE t.user.id = :userId AND t.deletedAt IS NULL "
            + "AND (:completed IS NULL OR t.completed = :completed) "
            + "AND (:keyword IS NULL OR LOWER(t.title) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))")
    Page<Todo> search(
            @Param("userId") Long userId,
            @Param("completed") Boolean completed,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
