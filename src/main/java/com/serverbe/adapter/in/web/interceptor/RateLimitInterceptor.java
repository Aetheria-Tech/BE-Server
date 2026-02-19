package com.serverbe.adapter.in.web.interceptor;

import com.serverbe.application.port.out.security.TokenResolver;
import com.serverbe.application.service.RateLimiterService;
import com.serverbe.domain.exception.server.ServerErrorCode;
import com.serverbe.domain.exception.server.ServerException;
import com.serverbe.infrastructure.security.TokenExtractor;
import com.serverbe.infrastructure.util.ClientIpUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 모든 웹 요청의 처리율 제한(Rate Limit)을 검사하는 인터셉터입니다.
 * 인증된 사용자는 User ID 기반으로, 비인증 사용자는 IP 주소 기반으로 제한을 적용합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterService rateLimiterService;
    private final TokenResolver tokenResolver;
    private final TokenExtractor tokenExtractor;

    /**
     * 컨트롤러 핸들러 호출 전 처리율을 제한합니다.
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. Preflight 요청은 통과 (CORS 제한 회피)
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }

        boolean isAllowed;
        String accessToken = extractAccessTokenSafely(request);

        // 2. 인증된 사용자: User ID 기반 Rate Limit 적용
        if (accessToken != null && tokenResolver.validateAccessToken(accessToken)) {
            Long userId = tokenResolver.getIdFromToken(accessToken);
            isAllowed = rateLimiterService.isAllowedForUser(userId);
        } else {
            // 3. 비로그인 사용자: IP 주소 기반 Rate Limit 적용
            String clientIp = ClientIpUtils.getClientIp(request);

            // IP 식별 불가 시 별도 로깅 후 공용 제한 버킷 사용
            if (ClientIpUtils.UNKNOWN_IP.equals(clientIp)) {
                log.warn("Could not identify Client IP. Request URI: {}", request.getRequestURI());
            }

            isAllowed = rateLimiterService.isAllowedForIp(clientIp);
        }

        // 4. 허용량 초과 시 429 Too Many Requests 예외 발생
        if (!isAllowed) {
            throw new ServerException(ServerErrorCode.TOO_MANY_REQUESTS);
        }

        return true;
    }

    /**
     * 토큰 추출 과정에서 발생할 수 있는 예외(포맷 에러 등)를 방어적으로 처리합니다.
     * 예외 발생 시 null을 반환하여 비인증(IP) 로직으로 자연스럽게 넘깁니다.
     *
     * @param request HTTP 요청
     * @return 추출된 액세스 토큰 또는 null
     */
    private String extractAccessTokenSafely(HttpServletRequest request) {
        try {
            return tokenExtractor.extractAccessToken(request);
        } catch (Exception e) {
            log.warn("Token extraction failed: {}", e.getMessage());
            return null;
        }
    }
}