package com.example.todoapp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * 첨부 API 통합 테스트 (CLAUDE.md 5장, ROADMAP Phase 12-2 완료 조건).
 *
 * <p>경로 탈출(../) 차단은 storageKey를 서버가 생성하므로 API로는 주입할 수 없다.
 * 해당 방어는 LocalStorageServiceTest에서 단위로 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttachmentIntegrationTest {

    private static final String ATTACHMENTS_URL = "/api/v1/attachments";

    /** PNG 매직바이트 + 패딩. 실제 이미지 디코딩은 하지 않으므로 헤더만 맞으면 된다. */
    private static final byte[] PNG_HEADER = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D
    };

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("presign → upload → complete 전 흐름")
    void fullUploadFlow() throws Exception {
        String token = signupAndGetToken(uniqueEmail());

        // 1. presign — TEMP 행 생성 + 업로드 URL 발급
        MvcResult presign = mockMvc.perform(post(ATTACHMENTS_URL + "/presign")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(presignJson("shot.png", "image/png", 1024)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadUrl").exists())
                .andExpect(jsonPath("$.data.storageKey").exists())
                .andReturn();

        String body = presign.getResponse().getContentAsString();
        Long attachmentId = extractLong(body, "\"attachmentId\":");
        String uploadUrl = extractString(body, "\"uploadUrl\":\"");
        // 저장 키 규칙: todos/{userId}/{yyyy}/{MM}/{uuid}.{ext}
        assertThat(extractString(body, "\"storageKey\":\"")).matches("todos/\\d+/\\d{4}/\\d{2}/[0-9a-f-]+\\.png");

        // 2. upload — Authorization 헤더 없이 서명 토큰만으로 인증된다(S3 presigned와 동일한 호출 방식)
        mockMvc.perform(put(URI.create(uploadUrl))
                        .contentType(MediaType.IMAGE_PNG)
                        .content(imageBytes(512)))
                .andExpect(status().isOk());

        // 3. complete — 실측 크기로 갱신되고 조회 URL이 발급된다
        mockMvc.perform(post(ATTACHMENTS_URL + "/" + attachmentId + "/complete")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileSize").value(512))
                .andExpect(jsonPath("$.data.originalFilename").value("shot.png"))
                .andExpect(jsonPath("$.data.viewUrl").exists());

        // 4. 일괄 조회 URL 발급
        mockMvc.perform(get(ATTACHMENTS_URL + "/urls")
                        .param("ids", String.valueOf(attachmentId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(attachmentId))
                .andExpect(jsonPath("$.data[0].viewUrl").exists());
    }

    @Test
    @DisplayName("조회 URL로 파일을 내려받으면 ApiResponse가 아니라 바이너리가 온다")
    void rawReturnsBinary() throws Exception {
        String token = signupAndGetToken(uniqueEmail());
        Long attachmentId = uploadImage(token, 256);

        MvcResult urls = mockMvc.perform(get(ATTACHMENTS_URL + "/urls")
                        .param("ids", String.valueOf(attachmentId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String viewUrl = extractString(urls.getResponse().getContentAsString(), "\"viewUrl\":\"");

        mockMvc.perform(get(URI.create(viewUrl)))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    assertThat(result.getResponse().getContentType()).startsWith("image/png");
                    assertThat(result.getResponse().getHeader("Content-Disposition")).isEqualTo("inline");
                    assertThat(result.getResponse().getContentAsByteArray()).hasSize(256);
                });
    }

    @Test
    @DisplayName("타인의 첨부는 404 — 존재 여부를 노출하지 않는다")
    void otherUserGetsNotFound() throws Exception {
        String tokenA = signupAndGetToken(uniqueEmail());
        String tokenB = signupAndGetToken(uniqueEmail());
        Long attachmentId = uploadImage(tokenA, 128);

        mockMvc.perform(post(ATTACHMENTS_URL + "/" + attachmentId + "/complete")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ATTACHMENT_NOT_FOUND"));

        mockMvc.perform(delete(ATTACHMENTS_URL + "/" + attachmentId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ATTACHMENT_NOT_FOUND"));

        // 일괄 조회에서는 404가 아니라 결과에서 조용히 빠진다.
        mockMvc.perform(get(ATTACHMENTS_URL + "/urls")
                        .param("ids", String.valueOf(attachmentId))
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("허용하지 않은 형식과 크기 초과는 400")
    void rejectsInvalidInput() throws Exception {
        String token = signupAndGetToken(uniqueEmail());

        mockMvc.perform(post(ATTACHMENTS_URL + "/presign")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(presignJson("virus.exe", "application/x-msdownload", 1024)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        mockMvc.perform(post(ATTACHMENTS_URL + "/presign")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(presignJson("huge.png", "image/png", 10 * 1024 * 1024)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("확장자만 이미지인 파일은 업로드 단계에서 거부된다")
    void rejectsFakeImageOnUpload() throws Exception {
        String token = signupAndGetToken(uniqueEmail());
        String uploadUrl = presignAndGetUploadUrl(token);

        mockMvc.perform(put(URI.create(uploadUrl))
                        .contentType(MediaType.IMAGE_PNG)
                        .content("MZ this is an executable".getBytes()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("서명 토큰이 없거나 위조되면 401")
    void rejectsBadToken() throws Exception {
        String token = signupAndGetToken(uniqueEmail());
        Long attachmentId = uploadImage(token, 64);

        mockMvc.perform(get(ATTACHMENTS_URL + "/" + attachmentId + "/raw").param("token", "not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        // Access Token은 첨부 토큰으로 통하지 않는다(typ 클레임이 없다).
        mockMvc.perform(get(ATTACHMENTS_URL + "/" + attachmentId + "/raw").param("token", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("토큰이 가리키는 첨부와 경로의 id가 다르면 401")
    void rejectsTokenForDifferentAttachment() throws Exception {
        String token = signupAndGetToken(uniqueEmail());
        Long first = uploadImage(token, 64);
        Long second = uploadImage(token, 64);

        MvcResult urls = mockMvc.perform(get(ATTACHMENTS_URL + "/urls")
                        .param("ids", String.valueOf(first))
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        String firstViewUrl = extractString(urls.getResponse().getContentAsString(), "\"viewUrl\":\"");
        String firstToken = firstViewUrl.substring(firstViewUrl.indexOf("token=") + 6);

        // first의 토큰으로 second를 읽으려는 시도
        mockMvc.perform(get(ATTACHMENTS_URL + "/" + second + "/raw").param("token", firstToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("삭제하면 조회에서 빠진다 (Soft Delete)")
    void softDeleteRemovesFromView() throws Exception {
        String token = signupAndGetToken(uniqueEmail());
        Long attachmentId = uploadImage(token, 64);

        mockMvc.perform(delete(ATTACHMENTS_URL + "/" + attachmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get(ATTACHMENTS_URL + "/urls")
                        .param("ids", String.valueOf(attachmentId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("token 파라미터가 없으면 500이 아니라 400")
    void missingTokenParamIsBadRequest() throws Exception {
        // 필수 쿼리 파라미터 누락은 서버 오류가 아니라 잘못된 요청이다.
        // 전용 핸들러가 없으면 catch-all에 걸려 500 INTERNAL_ERROR가 나간다 (CLAUDE.md 11장).
        mockMvc.perform(get(ATTACHMENTS_URL + "/1/raw"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("경로 id가 숫자가 아니면 500이 아니라 400")
    void nonNumericIdIsBadRequest() throws Exception {
        String token = signupAndGetToken(uniqueEmail());

        mockMvc.perform(post(ATTACHMENTS_URL + "/abc/complete").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("인증 없이 presign을 호출하면 401")
    void presignRequiresAuth() throws Exception {
        mockMvc.perform(post(ATTACHMENTS_URL + "/presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(presignJson("a.png", "image/png", 100)))
                .andExpect(status().isUnauthorized());
    }

    // --- 헬퍼 ---

    /** presign → upload를 마치고 attachmentId를 돌려준다. complete는 호출하지 않는다. */
    private Long uploadImage(String token, int size) throws Exception {
        MvcResult presign = mockMvc.perform(post(ATTACHMENTS_URL + "/presign")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(presignJson("img.png", "image/png", size)))
                .andExpect(status().isOk())
                .andReturn();
        String body = presign.getResponse().getContentAsString();
        Long id = extractLong(body, "\"attachmentId\":");

        mockMvc.perform(put(URI.create(extractString(body, "\"uploadUrl\":\"")))
                        .contentType(MediaType.IMAGE_PNG)
                        .content(imageBytes(size)))
                .andExpect(status().isOk());
        return id;
    }

    private String presignAndGetUploadUrl(String token) throws Exception {
        MvcResult presign = mockMvc.perform(post(ATTACHMENTS_URL + "/presign")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(presignJson("img.png", "image/png", 1024)))
                .andExpect(status().isOk())
                .andReturn();
        return extractString(presign.getResponse().getContentAsString(), "\"uploadUrl\":\"");
    }

    private byte[] imageBytes(int totalSize) {
        byte[] data = new byte[Math.max(totalSize, PNG_HEADER.length)];
        System.arraycopy(PNG_HEADER, 0, data, 0, PNG_HEADER.length);
        return data;
    }

    private String presignJson(String filename, String contentType, long fileSize) {
        return "{\"filename\":\"%s\",\"contentType\":\"%s\",\"fileSize\":%d}"
                .formatted(filename, contentType, fileSize);
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
        return "attach-test-" + System.nanoTime() + "@example.com";
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
