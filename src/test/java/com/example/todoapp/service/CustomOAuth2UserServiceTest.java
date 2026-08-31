package com.example.todoapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.todoapp.domain.AuthProvider;
import com.example.todoapp.domain.User;
import com.example.todoapp.domain.UserRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

/**
 * OAuth2 흐름은 실제 구글 서버와 통신하므로 MockMvc 통합 테스트로 끝까지 검증할 수 없다
 * (CLAUDE.md 14장). resolveUser(Map)만 순수 단위 테스트로 검증한다(테스트 8번).
 */
@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomOAuth2UserService customOAuth2UserService;

    @Test
    void 신규_이메일이면_GOOGLE_계정으로_가입한다() {
        Map<String, Object> attributes = Map.of("email", "new@example.com", "name", "구글사용자", "sub", "google-123");
        when(userRepository.findByEmailAndDeletedAtIsNull("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = customOAuth2UserService.resolveUser(attributes);

        assertThat(result.getEmail()).isEqualTo("new@example.com");
        assertThat(result.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(result.getNickname()).isEqualTo("구글사용자");
    }

    @Test
    void 기존_GOOGLE_계정이면_그대로_조회하고_중복_생성하지_않는다() {
        User existing = User.createGoogle("exist@example.com", "기존", "google-999");
        Map<String, Object> attributes = Map.of("email", "exist@example.com", "name", "기존", "sub", "google-999");
        when(userRepository.findByEmailAndDeletedAtIsNull("exist@example.com")).thenReturn(Optional.of(existing));

        User result = customOAuth2UserService.resolveUser(attributes);

        assertThat(result).isSameAs(existing);
    }

    @Test
    void 동일_이메일_LOCAL_계정이_있으면_충돌로_거부한다() {
        User localUser = User.createLocal("conflict@example.com", "encodedPw", "로컬유저");
        Map<String, Object> attributes = Map.of("email", "conflict@example.com", "name", "구글이름", "sub", "google-1");
        when(userRepository.findByEmailAndDeletedAtIsNull("conflict@example.com")).thenReturn(Optional.of(localUser));

        assertThatThrownBy(() -> customOAuth2UserService.resolveUser(attributes))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void 이름이_없으면_이메일_앞부분을_닉네임으로_쓴다() {
        Map<String, Object> attributes = Map.of("email", "noname@example.com", "sub", "google-2");
        when(userRepository.findByEmailAndDeletedAtIsNull("noname@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = customOAuth2UserService.resolveUser(attributes);

        assertThat(result.getNickname()).isEqualTo("noname");
    }

    @Test
    void 이름이_50자_초과면_50자로_절삭한다() {
        String longName = "가".repeat(60);
        Map<String, Object> attributes = Map.of("email", "long@example.com", "name", longName, "sub", "google-3");
        when(userRepository.findByEmailAndDeletedAtIsNull("long@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = customOAuth2UserService.resolveUser(attributes);

        assertThat(result.getNickname()).hasSize(50);
    }
}
