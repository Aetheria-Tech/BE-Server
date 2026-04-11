package com.serverbe.infrastructure.config.aop;

// ... 기존 import 문 ...
import com.serverbe.application.annotation.RateLimit;
import com.serverbe.application.port.out.security.TokenResolver;
import com.serverbe.application.service.RateLimiterService;
import com.serverbe.domain.exception.auth.AuthErrorCode;
import com.serverbe.domain.exception.auth.AuthException;
import com.serverbe.domain.exception.server.RateLimitExceededException;
import com.serverbe.domain.exception.server.ServerErrorCode;
import com.serverbe.infrastructure.security.TokenExtractor;
import com.serverbe.infrastructure.util.ClientIpUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature; // ✨ 추가
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimiterService rateLimiterService;
    private final TokenExtractor tokenExtractor;
    private final TokenResolver tokenResolver;

    // ✨ 포인트컷: 단일 어노테이션이든, 다중(Repeatable) 어노테이션이든 모두 캐치하도록 수정
    @Around("@annotation(com.serverbe.application.annotation.RateLimit) || @annotation(com.serverbe.application.annotation.RateLimits)")
    public Object checkRateLimit(ProceedingJoinPoint joinPoint) throws Throwable {

        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String endpoint = request.getRequestURI();

        // 1. 현재 실행되는 메서드 정보를 가져옴
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 2. getAnnotationsByType을 사용하면 1개든 2개든 배열 형태로 모두 가져옵니다.
        RateLimit[] rateLimits = method.getAnnotationsByType(RateLimit.class);

        // 3. 부여된 모든 정책을 순회하며 하나라도 위반하면 Exception 발생
        for (RateLimit rateLimit : rateLimits) {
            boolean isAllowed = true;

            if (rateLimit.target() == RateLimit.TargetType.IP) {
                String ip = ClientIpUtils.getClientIp(request);
                isAllowed = rateLimiterService.isAllowedForIp(ip, endpoint, rateLimit.capacity(), rateLimit.refillRate());

            } else if (rateLimit.target() == RateLimit.TargetType.USER) {
                String accessToken = tokenExtractor.extractAccessToken(request);

                if (!StringUtils.hasText(accessToken) || !tokenResolver.validateAccessToken(accessToken)) {
                    log.warn("[AOP Rate Limit] 유효하지 않은 토큰입니다.");
                    throw new AuthException(AuthErrorCode.JWT_TOKEN_IS_EMPTY);
                }

                Long userId = tokenResolver.getIdFromToken(accessToken);
                isAllowed = rateLimiterService.isAllowedForUser(userId, endpoint, rateLimit.capacity(), rateLimit.refillRate());
            }

            if (!isAllowed) {
                log.warn("[AOP Rate Limit] 너무 많은 요청입니다. 위반 타겟: {}, 엔드포인트: {}", rateLimit.target(), endpoint);

                throw new RateLimitExceededException(
                        ServerErrorCode.TOO_MANY_REQUESTS,
                        rateLimit.retryAfterSeconds()
                );
            }
        }

        // 모든 RateLimit 정책을 통과했다면 실제 메서드 실행
        return joinPoint.proceed();
    }
}