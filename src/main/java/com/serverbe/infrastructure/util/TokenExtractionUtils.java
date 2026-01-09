package com.serverbe.infrastructure.util;

import com.serverbe.infrastructure.config.properties.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;

/**
 * @responsibility HTTP 요청의 <b>Header</b> 또는 <b>Cookie</b>에 포함된 인증 토큰(Access/Refresh)을 식별하고 추출하는 역할을 수행합니다.
 * @implSpec
 * 1. <b>Access Token</b>: 표준 규약에 따라 {@code Authorization} 헤더의 <b>Bearer</b> 접두어 뒤의 값을 추출합니다.<br>
 * 2. <b>Refresh Token</b>: 보안을 위해 브라우저 쿠키에 저장된 값을 설정 파일({@link JwtProperties})의 쿠키명을 기준으로 추출합니다.
 */
@Slf4j
@Component
public class TokenExtractionUtils {
    private final String bearerPrefix;
    private final String refreshTokenCookie;

    /**
     * @responsibility 시스템 설정({@link JwtProperties})을 주입받아 토큰 추출에 필요한 접두어와 쿠키 이름을 초기화합니다.
     * @param jwtProperties JWT 관련 환경 설정 정보
     */
    public TokenExtractionUtils(JwtProperties jwtProperties) {
        this.bearerPrefix = jwtProperties.accessToken().prefix();
        this.refreshTokenCookie = jwtProperties.refreshToken().cookie();
    }

    /**
     * @responsibility {@link HttpServletRequest}의 <b>Authorization</b> 헤더에서 액세스 토큰을 추출합니다.
     * @implNote
     * 1. 헤더 값이 존재하고 설정된 접두어(예: "Bearer ")로 시작하는지 검사합니다.<br>
     * 2. 접두어 이후의 순수한 토큰 문자열만을 반환합니다.
     * @param request 현재 HTTP 요청 객체
     * @return 추출된 Access Token 문자열 (조건 미충족 시 {@code null})
     */
    public String extractAccessToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(bearerPrefix)) {
            return bearerToken.substring(bearerPrefix.length());
        }

        return null;
    }

    /**
     * @responsibility {@link HttpServletRequest}의 <b>Cookie</b> 목록에서 리프레시 토큰을 찾아 반환합니다.
     * @implNote {@link JwtProperties}에 정의된 {@code refreshTokenCookie} 이름과 일치하는 첫 번째 쿠키의 값을 가져옵니다.
     * @param request 현재 HTTP 요청 객체
     * @return 추출된 Refresh Token 문자열 (없으면 {@code null})
     */
    public String extractRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return null;
        }

        // 설정 파일에서 정의한 쿠키 이름을 동적으로 가져옵니다.
        String cookieName = refreshTokenCookie;

        return Arrays.stream(cookies)
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}