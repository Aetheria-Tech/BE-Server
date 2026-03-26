package com.serverbe.infrastructure.config.aop;

import com.serverbe.application.annotation.RateLimit;
import com.serverbe.application.service.RateLimiterService;
import com.serverbe.domain.exception.server.ServerErrorCode;
import com.serverbe.domain.exception.server.ServerException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimiterService rateLimiterService;

    // @RateLimit 어노테이션이 붙은 메서드를 가로챕니다.
    // 사용 예시 = @RateLimit(capacity = 3, refillRate = 3, target = RateLimit.TargetType.IP)
    @Around("@annotation(rateLimit)")
    public Object checkRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        boolean isAllowed = true;

        if (rateLimit.target() == RateLimit.TargetType.IP) {
            String ip = getClientIp(request);
            // TODO: 서비스 로직에 capacity, refillRate를 직접 넘길 수 있도록 오버로딩된 메서드 필요
            isAllowed = rateLimiterService.isAllowedForIp(ip); 
        } else if (rateLimit.target() == RateLimit.TargetType.USER) {
            // 예시: 헤더나 세션에서 userId 추출 로직
            Long userId = 1L; // 임시 값
            isAllowed = rateLimiterService.isAllowedForUser(userId);
        }

        if (!isAllowed) {
            log.warn("[AOP Rate Limit] 너무 많은 요청입니다. 타겟: {}", rateLimit.target());
            // 에러를 던져서 GlobalExceptionHandler에서 429 응답을 주도록 처리
            throw new ServerException(ServerErrorCode.TOO_MANY_REQUESTS);
        }

        // 통과했다면 원래 컨트롤러 로직 실행
        return joinPoint.proceed();
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}