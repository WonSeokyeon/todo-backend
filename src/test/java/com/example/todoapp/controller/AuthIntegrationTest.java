package com.example.todoapp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * CLAUDE.md 6장(Access+Refresh 2-토큰) · 11장(에러 코드) 요구사항을 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    private static final String SIGNUP_URL = "/api/v1/auth/signup";
    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String REFRESH_URL = "/api/v1/auth/refresh";
    private static final String LOGOUT_URL = "/api/v1/auth/logout";
    private static final String ME_URL = "/api/v1/auth/me";
    private static final String REFRESH_COOKIE = "refresh_token";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 회원가입_성공_및_중복이메일_거부() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(email, "pass1234", "테스터")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists());

        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(email, "pass1234", "테스터2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_DUPLICATED"));
    }

    @Test
    void 한글_25자_비밀번호는_400_INVALID_INPUT() throws Exception {
        String koreanPassword = "가".repeat(25); // 75바이트 > 72바이트 한계

        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(uniqueEmail(), koreanPassword, "테스터")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void 로그인_성공시_AccessToken과_RefreshCookie_발급() throws Exception {
        String email = uniqueEmail();
        signup(email, "pass1234", "테스터");

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "pass1234")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(cookie().exists(REFRESH_COOKIE));
    }

    @Test
    void 미가입_이메일과_비밀번호_오류는_동일한_401_메시지() throws Exception {
        String email = uniqueEmail();
        signup(email, "pass1234", "테스터");

        MvcResult wrongPassword = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "wrongpass")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult unknownEmail = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(uniqueEmail(), "whatever1")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(wrongPassword.getResponse().getContentAsString())
                .isEqualTo(unknownEmail.getResponse().getContentAsString());
    }

    @Test
    void 토큰없이_me_401_유효토큰_me_200() throws Exception {
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        String email = uniqueEmail();
        String accessToken = extractAccessToken(signup(email, "pass1234", "테스터"));

        mockMvc.perform(get(ME_URL).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.nickname").value("테스터"));
    }

    @Test
    void refresh_회전_성공과_재사용시_401_및_전체폐기() throws Exception {
        String email = uniqueEmail();
        MvcResult signupResult = signup(email, "pass1234", "테스터");
        Cookie originalRefreshCookie = signupResult.getResponse().getCookie(REFRESH_COOKIE);

        MvcResult firstRotate = mockMvc.perform(post(REFRESH_URL).cookie(originalRefreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(cookie().exists(REFRESH_COOKIE))
                .andReturn();
        Cookie rotatedCookie = firstRotate.getResponse().getCookie(REFRESH_COOKIE);
        assertThat(rotatedCookie.getValue()).isNotEqualTo(originalRefreshCookie.getValue());

        // 이미 폐기된 원래 토큰 재사용 시도 → 401 + 해당 사용자 전체 토큰 폐기
        mockMvc.perform(post(REFRESH_URL).cookie(originalRefreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));

        // 전체 폐기됐으므로 회전으로 얻은 새 토큰도 이제 무효
        mockMvc.perform(post(REFRESH_URL).cookie(rotatedCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logout_후_refresh_시도하면_401() throws Exception {
        String email = uniqueEmail();
        MvcResult signupResult = signup(email, "pass1234", "테스터");
        Cookie refreshCookie = signupResult.getResponse().getCookie(REFRESH_COOKIE);
        String accessToken = extractAccessToken(signupResult);

        mockMvc.perform(post(LOGOUT_URL)
                        .header("Authorization", "Bearer " + accessToken)
                        .cookie(refreshCookie))
                .andExpect(status().isOk());

        mockMvc.perform(post(REFRESH_URL).cookie(refreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    private MvcResult signup(String email, String password, String nickname) throws Exception {
        return mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(email, password, nickname)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String uniqueEmail() {
        return "auth-test-" + System.nanoTime() + "@example.com";
    }

    private String signupJson(String email, String password, String nickname) {
        return "{\"email\":\"%s\",\"password\":\"%s\",\"nickname\":\"%s\"}".formatted(email, password, nickname);
    }

    private String loginJson(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private String extractAccessToken(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        String marker = "\"accessToken\":\"";
        int start = body.indexOf(marker) + marker.length();
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }
}
