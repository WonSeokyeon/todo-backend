package com.example.todoapp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CloudFront는 커스텀 오리진 헤더로 지정한 X-Forwarded-Proto를 오리진에 전달하지 않고
 * 무시한다(2026-09-10 실측 확인). 대신 CloudFront가 항상 붙여 보내는
 * CloudFront-Forwarded-Proto 헤더를 읽어 request.getScheme()/isSecure()를 보정한다.
 *
 * <p>이 보정이 없으면 이 오리진(:8080, 평문 HTTP)이 CloudFront 뒤에 있다는 사실을
 * request 객체가 알 방법이 없어, OAuth2 redirect_uri가 http://로 생성되어
 * 구글의 승인된 리디렉션 URI(https)와 어긋난다.
 */
public class CloudFrontForwardedProtoFilter extends OncePerRequestFilter {

    private static final String CLOUDFRONT_FORWARDED_PROTO = "CloudFront-Forwarded-Proto";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String proto = request.getHeader(CLOUDFRONT_FORWARDED_PROTO);
        if ("https".equalsIgnoreCase(proto)) {
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
