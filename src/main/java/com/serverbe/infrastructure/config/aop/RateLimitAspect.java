package com.serverbe.infrastructure.config.aop;

import com.serverbe.application.annotation.RateLimit;
import com.serverbe.application.port.out.security.TokenResolver;
import com.serverbe.application.service.RateLimiterService;
import com.serverbe.domain.exception.auth.AuthErrorCode;
import com.serverbe.domain.exception.auth.AuthException;
import com.serverbe.domain.exception.server.RateLimitExceededException;
import com.serverbe.domain.exception.server.ServerErrorCode;
import com.serverbe.domain.exception.server.ServerException;
import com.serverbe.infrastructure.security.TokenExtractor;
import com.serverbe.infrastructure.util.ClientIpUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

// ... 기존 import 생략 ...

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimiterService rateLimiterService;
    private final TokenExtractor tokenExtractor;
    private final TokenResolver tokenResolver;

    @Around("@annotation(rateLimit)")
    public Object checkRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {

        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String endpoint = request.getRequestURI();

        boolean isAllowed = true;

        if (rateLimit.target() == RateLimit.TargetType.IP) {
            String ip = ClientIpUtils.getClientIp(request);
            isAllowed = rateLimiterService.isAllowedForIp(ip, endpoint, rateLimit.capacity(), rateLimit.refillRate());

        } else if (rateLimit.target() == RateLimit.TargetType.USER) {
            String accessToken = tokenExtractor.extractAccessToken(request);

            if (!StringUtils.hasText(accessToken)) {
                log.warn("[AOP Rate Limit] 유저 기반 제한이지만 토큰이 존재하지 않습니다.");
                throw new AuthException(AuthErrorCode.JWT_TOKEN_IS_EMPTY);
            }

            Long userId = tokenResolver.getIdFromToken(accessToken);
            isAllowed = rateLimiterService.isAllowedForUser(userId, endpoint, rateLimit.capacity(), rateLimit.refillRate());
        }

        if (!isAllowed) {
            log.warn("[AOP Rate Limit] 너무 많은 요청입니다. 타겟: {}, 엔드포인트: {}", rateLimit.target(), endpoint);

            throw new RateLimitExceededException(
                    ServerErrorCode.TOO_MANY_REQUESTS,
                    rateLimit.retryAfterSeconds()
            );
        }

        return joinPoint.proceed();
    }
}