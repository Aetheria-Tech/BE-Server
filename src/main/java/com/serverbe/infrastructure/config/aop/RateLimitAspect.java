package com.serverbe.infrastructure.config.aop;

import com.serverbe.application.annotation.RateLimit;
import com.serverbe.application.port.out.security.TokenResolver;
import com.serverbe.application.service.RateLimiterService;
import com.serverbe.domain.exception.auth.AuthErrorCode;
import com.serverbe.domain.exception.auth.AuthException;
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
        boolean isAllowed = true;

        if (rateLimit.target() == RateLimit.TargetType.IP) {
            String ip = ClientIpUtils.getClientIp(request);
            isAllowed = rateLimiterService.isAllowedForIp(ip, rateLimit.capacity(), rateLimit.refillRate());

        } else if (rateLimit.target() == RateLimit.TargetType.USER) {
            // 2. HTTP 요청에서 Access Token 추출
            String accessToken = tokenExtractor.extractAccessToken(request);

            // 3. 토큰 존재 여부 검증 (회원 전용 API인데 토큰이 없다면 401 반환)
            if (!StringUtils.hasText(accessToken)) {
                log.warn("[AOP Rate Limit] 유저 기반 제한이지만 토큰이 존재하지 않습니다.");
                throw new AuthException(AuthErrorCode.JWT_TOKEN_IS_EMPTY);
            }

            // 4. TokenResolver를 사용해 토큰에서 userId 파싱 (예외는 내부에서 처리됨)
            Long userId = tokenResolver.getIdFromToken(accessToken);

            isAllowed = rateLimiterService.isAllowedForUser(userId, rateLimit.capacity(), rateLimit.refillRate());
        }

        if (!isAllowed) {
            log.warn("[AOP Rate Limit] 너무 많은 요청입니다. 타겟: {}", rateLimit.target());
            throw new ServerException(ServerErrorCode.TOO_MANY_REQUESTS);
        }

        return joinPoint.proceed();
    }
}