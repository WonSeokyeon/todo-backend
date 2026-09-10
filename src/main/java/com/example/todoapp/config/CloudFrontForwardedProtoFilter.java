package com.example.todoapp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CloudFront는 X-Forwarded-Proto 헤더를 오리진에 전달하지 않고 무조건 제거하며,
 * CloudFront-Forwarded-Proto는 캐시 정책에서 별도로 "헤더 기반 캐싱"을 설정해야만
 * 붙는 헤더라 CachingDisabled 관리형 정책에서는 받을 수 없다(2026-09-10 AWS 공식
 * 문서로 확인, RequestAndResponseBehaviorCustomOrigin·header-caching 참조).
 *
 * <p>대신 CloudFront가 오리진으로 가는 모든 요청에 예외 없이 붙이는 X-Amz-Cf-Id
 * 헤더의 존재 여부로 "이 요청이 CloudFront를 거쳤는가"를 판단한다. 이 배포의
 * Viewer Protocol Policy가 "Redirect HTTP to HTTPS"이므로, CloudFront를 거친
 * 요청은 뷰어 입장에서 항상 HTTPS였음이 보장된다.
 *
 * <p>이 보정이 없으면 이 오리진(:8080, 평문 HTTP)이 CloudFront 뒤에 있다는 사실을
 * request 객체가 알 방법이 없어, OAuth2 redirect_uri가 http://로 생성되어
 * 구글의 승인된 리디렉션 URI(https)와 어긋난다.
 */
@Slf4j
public class CloudFrontForwardedProtoFilter extends OncePerRequestFilter {

    private static final String CLOUDFRONT_REQUEST_ID_HEADER = "X-Amz-Cf-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        boolean viaCloudFront = request.getHeader(CLOUDFRONT_REQUEST_ID_HEADER) != null;
        // TEMP DEBUG (2026-09-10) — 이 필터가 실제로 실행되는지, 조건이 맞는지 확인 후 제거할 것
        log.info("[TEMP-DEBUG-WRAP] uri={} viaCloudFront={} beforeScheme={}",
                request.getRequestURI(), viaCloudFront, request.getScheme());
        if (viaCloudFront) {
            filterChain.doFilter(new HttpsForcedRequestWrapper(request), response);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private static final class HttpsForcedRequestWrapper extends HttpServletRequestWrapper {
        HttpsForcedRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getScheme() {
            return "https";
        }

        @Override
        public boolean isSecure() {
            return true;
        }

        @Override
        public int getServerPort() {
            return 443;
        }
    }
}
