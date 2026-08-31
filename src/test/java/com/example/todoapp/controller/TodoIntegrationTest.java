package com.example.todoapp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * CLAUDE.md 5장(Todo API)·6장(XSS 이중 방어) 요구사항을 검증한다(통합 테스트 4~7번).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TodoIntegrationTest {

    private static final String TODOS_URL = "/api/v1/todos";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 생성_조회_수정_삭제() throws Exception {
        String token = signupAndGetToken(uniqueEmail());

        Long id = createTodo(token, "첫 할 일", "본문", "MEDIUM", "2026-09-01");

        mockMvc.perform(get(TODOS_URL + "/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("첫 할 일"))
                .andExpect(jsonPath("$.data.completed").value(false));

        // toggle로 완료 처리한 뒤 PUT을 해도 completed는 유지되어야 한다.
        mockMvc.perform(patch(TODOS_URL + "/" + id + "/toggle")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(put(TODOS_URL + "/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(todoJson("수정된 제목", "수정된 본문", "LOW", "2026-10-01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정된 제목"))
                .andExpect(jsonPath("$.data.completed").value(true));

        mockMvc.perform(delete(TODOS_URL + "/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get(TODOS_URL + "/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TODO_NOT_FOUND"));
    }

    @Test
    void 타사용자_접근은_404_입력값_검증은_400() throws Exception {
        String tokenA = signupAndGetToken(uniqueEmail());
        String tokenB = signupAndGetToken(uniqueEmail());
        Long id = createTodo(tokenA, "A의 할 일", "본문", "MEDIUM", null);

        mockMvc.perform(get(TODOS_URL + "/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TODO_NOT_FOUND"));

        mockMvc.perform(post(TODOS_URL)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(todoJson("", "본문", "MEDIUM", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        mockMvc.perform(post(TODOS_URL)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(todoJson("가".repeat(201), "본문", "MEDIUM", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        mockMvc.perform(post(TODOS_URL)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(todoJson("제목", "a".repeat(50001), "MEDIUM", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void XSS_정화_및_필터_검색_정렬화이트리스트() throws Exception {
        String token = signupAndGetToken(uniqueEmail());

        String maliciousContent = "<p>본문 <script>alert(1)</script><a href='http://evil.com'>링크</a></p>";
        Long id = createTodo(token, "보안테스트", maliciousContent, "HIGH", null);

        mockMvc.perform(get(TODOS_URL + "/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).doesNotContain("<script>");
                    assertThat(body).contains("rel=\\\"noopener noreferrer\\\"");
                    assertThat(body).contains("target=\\\"_blank\\\"");
                });

        createTodo(token, "회의 준비", "내용", "MEDIUM", null);
        mockMvc.perform(patch(TODOS_URL + "/" + id + "/toggle")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(TODOS_URL).param("completed", "true").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        // 대소문자를 섞어도 매칭되어야 한다.
        mockMvc.perform(get(TODOS_URL).param("keyword", "회의").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        // 화이트리스트 밖 sort 값이 들어와도 500이 아니라 200이어야 한다.
        mockMvc.perform(get(TODOS_URL).param("sort", "foo,desc").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void toggle_멱등성과_날짜_직렬화() throws Exception {
        String token = signupAndGetToken(uniqueEmail());
        Long id = createTodo(token, "멱등성 테스트", "본문", "MEDIUM", "2026-09-20");

        mockMvc.perform(patch(TODOS_URL + "/" + id + "/toggle")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.dueDate").value(instanceOf(String.class)))
                .andExpect(jsonPath("$.data.createdAt").value(instanceOf(String.class)));

        // 같은 값으로 다시 호출해도 결과가 동일해야 한다(멱등).
        mockMvc.perform(patch(TODOS_URL + "/" + id + "/toggle")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true));
    }

    private Long createTodo(String token, String title, String content, String priority, String dueDate) throws Exception {
        MvcResult result = mockMvc.perform(post(TODOS_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(todoJson(title, content, priority, dueDate)))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        String marker = "\"id\":";
        int start = body.indexOf(marker) + marker.length();
        int end = body.indexOf(',', start);
        return Long.valueOf(body.substring(start, end));
    }

    private String signupAndGetToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"pass1234\",\"nickname\":\"테스터\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        String marker = "\"accessToken\":\"";
        int start = body.indexOf(marker) + marker.length();
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }

    private String uniqueEmail() {
        return "todo-test-" + System.nanoTime() + "@example.com";
    }

    private String todoJson(String title, String content, String priority, String dueDate) {
        String dueDateField = dueDate == null ? "null" : "\"" + dueDate + "\"";
        return "{\"title\":\"%s\",\"content\":\"%s\",\"priority\":\"%s\",\"dueDate\":%s}"
                .formatted(escape(title), escape(content), priority, dueDateField);
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
