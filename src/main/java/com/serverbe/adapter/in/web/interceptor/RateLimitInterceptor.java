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

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterService rateLimiterService;
    private final TokenResolver tokenResolver;
    private final TokenExtractor tokenExtractor;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. Preflight 요청은 통과
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }

        boolean isAllowed;
        String accessToken = extractAccessTokenSafely(request);

        // 2. 토큰 유효성 검증 및 분기 처리
        if (accessToken != null && tokenResolver.validateAccessToken(accessToken)) {
            Long userId = tokenResolver.getIdFromToken(accessToken);
            isAllowed = rateLimiterService.isAllowedForUser(userId);
        } else {
            // 3. 비로그인 사용자 (IP 기준)
            String clientIp = ClientIpUtils.getClientIp(request);

            //  UNKNOWN인 경우 로그를 남기고, 공용 버킷(rate:ip:UNKNOWN)을 사용하여 제한
            if (ClientIpUtils.UNKNOWN_IP.equals(clientIp)) {
                log.warn("Could not identify Client IP. Request URI: {}", request.getRequestURI());
            }

            isAllowed = rateLimiterService.isAllowedForIp(clientIp);
        }

        // 4. 허용량 초과 시 예외 발생
        if (!isAllowed) {
            throw new ServerException(ServerErrorCode.TOO_MANY_REQUESTS);
        }

        return true;
    }

    /**
     * 토큰 추출 과정에서 발생할 수 있는 예외를 방어적으로 처리
     */
    private String extractAccessTokenSafely(HttpServletRequest request) {
        try {
            return tokenExtractor.extractAccessToken(request);
        } catch (Exception e) {
            log.warn("Token extraction failed");
            return null; // 토큰 추출 실패 시 비로그인(IP) 로직으로 넘김
        }
    }
}