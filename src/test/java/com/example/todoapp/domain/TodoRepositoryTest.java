package com.example.todoapp.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class TodoRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TodoRepository todoRepository;

    @Test
    void softDelete된_Todo는_findByUserIdAndDeletedAtIsNull_결과에서_제외된다() {
        User user = userRepository.saveAndFlush(User.createLocal("owner@example.com", "pw", "소유자"));

        Todo alive = Todo.create(user, "살아있는 할일", null, Priority.MEDIUM, null);
        Todo deleted = Todo.create(user, "삭제된 할일", null, Priority.LOW, null);
        deleted.softDelete();
        todoRepository.saveAllAndFlush(List.of(alive, deleted));

        List<Todo> result = todoRepository.findByUserIdAndDeletedAtIsNull(user.getId());

        assertThat(result).extracting(Todo::getTitle).containsExactly("살아있는 할일");
    }

    @Test
    void findByIdAndDeletedAtIsNull은_삭제되지_않은_Todo만_반환한다() {
        User user = userRepository.saveAndFlush(User.createLocal("owner2@example.com", "pw", "소유자2"));
        Todo todo = todoRepository.saveAndFlush(Todo.create(user, "제목", "본문", Priority.HIGH, null));

        assertThat(todoRepository.findByIdAndDeletedAtIsNull(todo.getId())).isPresent();

        todo.softDelete();
        todoRepository.saveAndFlush(todo);

        assertThat(todoRepository.findByIdAndDeletedAtIsNull(todo.getId())).isEmpty();
    }
}
