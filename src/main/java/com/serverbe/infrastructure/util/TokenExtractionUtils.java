package com.serverbe.infrastructure.util;

import com.serverbe.infrastructure.config.properties.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;

/**
 * HTTP 요청에서 JWT 토큰(Access/Refresh)을 추출하는 컴포넌트입니다.
 * <p>
 * JwtProperties 설정을 참조하여 토큰을 추출하며,
 * 스프링 빈으로 관리되므로 필요한 곳에서 의존성 주입을 받아 사용합니다.
 */
@Component
public class TokenExtractionUtils {
    private final String BEARER_PREFIX;
    private final String REFRESH_TOKEN_COOKIE;

    public TokenExtractionUtils(JwtProperties jwtProperties) {
        this.BEARER_PREFIX = jwtProperties.accessToken().prefix();
        this.REFRESH_TOKEN_COOKIE = jwtProperties.refreshToken().cookie();
    }

    /**
     * Authorization 헤더에서 Access Token을 추출합니다.
     *
     * @param request HttpServletRequest
     * @return Access Token 문자열 (없으면 null)
     */
    public String extractAccessToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }

        return null;
    }

    /**
     * 쿠키에서 Refresh Token을 추출합니다.
     *
     * @param request HttpServletRequest
     * @return Refresh Token 문자열 (없으면 null)
     */
    public String extractRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return null;
        }

        // 설정 파일에서 정의한 쿠키 이름을 동적으로 가져옵니다.
        String cookieName = REFRESH_TOKEN_COOKIE;

        return Arrays.stream(cookies)
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}