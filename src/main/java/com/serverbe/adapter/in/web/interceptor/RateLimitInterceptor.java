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
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterService rateLimiterService;
    private final TokenResolver tokenResolver;
    private final TokenExtractor tokenExtractor;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Preflight 요청(OPTIONS)은 통과
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }

        String accessToken = tokenExtractor.extractAccessToken(request);
        boolean isAllowed;

        if (accessToken != null && tokenResolver.validateAccessToken(accessToken)) {
            // 1. 인증된 사용자: User ID 기준 제한
            Long userId = tokenResolver.getIdFromToken(accessToken);
            isAllowed = rateLimiterService.isAllowedForUser(userId);
        } else {
            // 2. 비인증 사용자: IP 기준 제한
            String clientIp = ClientIpUtils.getClientIp(request);
            isAllowed = rateLimiterService.isAllowedForIp(clientIp);
        }

        if (!isAllowed) {
            throw new ServerException(ServerErrorCode.TOO_MANY_REQUESTS);
        }

        return true;
    }
}