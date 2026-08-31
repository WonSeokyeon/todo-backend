package com.example.todoapp.service;

import com.example.todoapp.config.CustomOAuth2User;
import com.example.todoapp.domain.AuthProvider;
import com.example.todoapp.domain.User;
import com.example.todoapp.domain.UserRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구글 로그인 신규가입/기존조회/충돌거부 (CLAUDE.md 6장).
 * 동일 이메일의 LOCAL 계정이 있으면 자동 연동하지 않고 거부한다.
 */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final int NICKNAME_MAX_LENGTH = 50;

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        User user = resolveUser(oAuth2User.getAttributes());
        return new CustomOAuth2User(user, oAuth2User.getAttributes());
    }

    /**
     * 실제 구글 통신(super.loadUser) 없이 attributes만으로 테스트할 수 있도록 분리했다.
     */
    @Transactional
    User resolveUser(Map<String, Object> attributes) {
        String email = (String) attributes.get("email");
        String providerId = (String) attributes.get("sub");
        String googleName = (String) attributes.get("name");

        return userRepository.findByEmailAndDeletedAtIsNull(email)
                .map(existing -> requireGoogleProvider(existing))
                .orElseGet(() -> userRepository.save(
                        User.createGoogle(email, resolveNickname(googleName, email), providerId)));
    }

    private User requireGoogleProvider(User existing) {
        if (existing.getProvider() != AuthProvider.GOOGLE) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email_conflict"),
                    "이미 이메일로 가입된 계정입니다. 이메일로 로그인해 주세요."
            );
        }
        return existing;
    }

    private String resolveNickname(String googleName, String email) {
        String nickname = (googleName == null || googleName.isBlank())
                ? email.substring(0, email.indexOf('@'))
                : googleName;
        return nickname.length() > NICKNAME_MAX_LENGTH ? nickname.substring(0, NICKNAME_MAX_LENGTH) : nickname;
    }
}
