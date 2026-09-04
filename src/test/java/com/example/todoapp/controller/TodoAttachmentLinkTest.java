package com.example.todoapp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.todoapp.domain.Attachment;
import com.example.todoapp.domain.AttachmentRepository;
import com.example.todoapp.domain.AttachmentStatus;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 할 일 저장 시 첨부 연결 처리 검증 (ROADMAP Phase 12-3 완료 조건, DoD 4·5·7번).
 *
 * <p>본문에 남은 첨부는 LINKED로 전환되고, 사라진 첨부는 Soft Delete되며,
 * 저장된 HTML에는 src 없이 data-attachment-id만 남아야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TodoAttachmentLinkTest {

    private static final String TODOS_URL = "/api/v1/todos";
    private static final String ATTACHMENTS_URL = "/api/v1/attachments";

    private static final byte[] PNG_HEADER = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Test
    @DisplayName("본문에 넣은 첨부는 LINKED가 되고 todo_id가 채워진다 (DoD 4)")
    void linksAttachmentOnCreate() throws Exception {
        String token = signupAndGetToken(uniqueEmail());
        Long attachmentId = uploadImage(token);

        Long todoId = createTodo(token, "회의 자료",
                "<p>회의</p><img data-attachment-id=\"" + attachmentId + "\" alt=\"스샷\">");

        Attachment attachment = attachmentRepository.findById(attachmentId).orElseThrow();
        assertThat(attachment.getStatus()).isEqualTo(AttachmentStatus.LINKED);
        assertThat(attachment.getTodo().getId()).isEqualTo(todoId);
        assertThat(attachment.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("저장된 HTML에 src가 없고 data-attachment-id만 남는다 (DoD 5)")
    void storesIdWithoutSrc() throws Exception {
        String token = signupAndGetToken(uniqueEmail());
        Long attachmentId = uploadImage(token);

        // 프론트 에디터는 화면 표시용 src를 붙여 보낸다. 서버는 그것을 저장하지 않아야 한다.
        Long todoId = createTodo(token, "src 제거 확인",
                "<img data-attachment-id=\"" + attachmentId + "\" src=\"http://localhost:8080/expiring?token=x\">");

        MvcResult result = mockMvc.perform(get(TODOS_URL + "/" + todoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("data-attachment-id=\\\"" + attachmentId + "\\\"");
        assertThat(body).doesNotContain("expiring");
        assertThat(body).doesNotContain("src=");
    }

    @Test
    @DisplayName("본문에서 이미지를 지우고 저장하면 Soft Delete된다 (DoD 7)")
    void softDeletesRemovedAttachment() throws Exception {
        String token = signupAndGetToken(uniqueEmail());
        Long attachmentId = uploadImage(token);

        Long todoId = createTodo(token, "삭제 확인",
                "<img data-attachment-id=\"" + attachmentId + "\">");
        assertThat(attachmentRepository.findById(attachmentId).orElseThrow().getDeletedAt()).isNull();

        // 본문에서 이미지를 빼고 수정
        mockMvc.perform(put(TODOS_URL + "/" + todoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(todoJson("삭제 확인", "<p>이미지를 뺐다</p>")))
                .andExpect(status().isOk());

        assertThat(attachmentRepository.findById(attachmentId).orElseThrow().getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("타인의 첨부 ID를 본문에 넣으면 404로 거부된다")
    void rejectsOtherUsersAttachment() throws Exception {
        String tokenA = signupAndGetToken(uniqueEmail());
        String tokenB = signupAndGetToken(uniqueEmail());
        Long attachmentOfA = uploadImage(tokenA);

        mockMvc.perform(post(TODOS_URL)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(todoJson("남의 것 훔치기",
                                "<img data-attachment-id=\"" + attachmentOfA + "\">")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ATTACHMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("존재하지 않는 첨부 ID를 넣으면 404로 거부된다")
    void rejectsUnknownAttachment() throws Exception {
        String token = signupAndGetToken(uniqueEmail());

        mockMvc.perform(post(TODOS_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(todoJson("없는 첨부", "<img data-attachment-id=\"99999999\">")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ATTACHMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("첨부가 없는 할 일은 기존과 동일하게 동작한다")
    void worksWithoutAttachments() throws Exception {
        String token = signupAndGetToken(uniqueEmail());

        Long todoId = createTodo(token, "첨부 없음", "<p>그냥 글</p>");

        mockMvc.perform(get(TODOS_URL + "/" + todoId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("첨부 없음"));
    }

    // --- 헬퍼 ---

    private Long uploadImage(String token) throws Exception {
        MvcResult presign = mockMvc.perform(post(ATTACHMENTS_URL + "/presign")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"a.png\",\"contentType\":\"image/png\",\"fileSize\":256}"))
                .andExpect(status().isOk())
                .andReturn();
        String body = presign.getResponse().getContentAsString();
        Long id = extractLong(body, "\"attachmentId\":");

        byte[] data = new byte[256];
        System.arraycopy(PNG_HEADER, 0, data, 0, PNG_HEADER.length);
        mockMvc.perform(put(URI.create(extractString(body, "\"uploadUrl\":\"")))
                        .contentType(MediaType.IMAGE_PNG)
                        .content(data))
                .andExpect(status().isOk());

        mockMvc.perform(post(ATTACHMENTS_URL + "/" + id + "/complete")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        return id;
    }

    private Long createTodo(String token, String title, String content) throws Exception {
        MvcResult result = mockMvc.perform(post(TODOS_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(todoJson(title, content)))
                .andExpect(status().isOk())
                .andReturn();
        return extractLong(result.getResponse().getContentAsString(), "\"id\":");
    }

    private String todoJson(String title, String content) {
        return "{\"title\":\"%s\",\"content\":\"%s\",\"priority\":\"MEDIUM\",\"dueDate\":null}"
                .formatted(title, content.replace("\\", "\\\\").replace("\"", "\\\""));
    }

    private String signupAndGetToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"pass1234\",\"nickname\":\"테스터\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return extractString(result.getResponse().getContentAsString(), "\"accessToken\":\"");
    }

    private String uniqueEmail() {
        return "link-test-" + System.nanoTime() + "@example.com";
    }

    private Long extractLong(String body, String marker) {
        int start = body.indexOf(marker) + marker.length();
        int end = start;
        while (end < body.length() && Character.isDigit(body.charAt(end))) {
            end++;
        }
        return Long.valueOf(body.substring(start, end));
    }

    private String extractString(String body, String marker) {
        int start = body.indexOf(marker) + marker.length();
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }
}
