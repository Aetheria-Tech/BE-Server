package com.serverbe.application.service;

import com.serverbe.application.port.in.ratelimit.RateLimitUseCase;
import com.serverbe.application.port.out.ratelimit.RateLimitPort;
import com.serverbe.infrastructure.config.properties.RateLimitProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.github.benmanes.caffeine.cache.Cache;

/**
 * 처리율 제한(Rate Limit) 비즈니스 로직을 처리하는 서비스 클래스입니다.
 * {@link RateLimitPort}를 통해 인프라 계층(Redis 등)과 통신하며,
 * 설정된 정책({@link RateLimitProperties})에 따라 요청 허용 여부를 결정합니다.
 */
@Slf4j
@Service
public class RateLimiterService implements RateLimitUseCase {

    private final RateLimitPort rateLimitPort;
    private final Cache<String, Integer> localRateLimitCache;

    // 비즈니스 설정 값들
    private final int userCapacity;
    private final int userRefillRate;
    private final int ipCapacity;
    private final int ipRefillRate;
    private final String userPrefix;
    private final String ipPrefix;

    /**
     * 의존성 주입 및 설정 파일에서 정책 값을 초기화합니다.
     */
    public RateLimiterService(
            RateLimitPort rateLimitPort,
            RateLimitProperties rateLimitProperties,
            Cache<String, Integer> localRateLimitCache
    ) {
        this.rateLimitPort = rateLimitPort;

        // RateLimitProperties에서 설정 값을 로드하여 필드에 저장
        this.userCapacity = rateLimitProperties.user().capacity();
        this.userRefillRate = rateLimitProperties.user().refillRate();
        this.ipCapacity = rateLimitProperties.ip().capacity();
        this.ipRefillRate = rateLimitProperties.ip().refillRate();
        this.userPrefix = rateLimitProperties.prefix().user();
        this.ipPrefix = rateLimitProperties.prefix().ip();
        this.localRateLimitCache = localRateLimitCache;
    }

    /**
     * 인증된 사용자 ID 기반의 요청 허용 여부를 확인합니다.
     * @param userId 사용자 식별자
     * @return true(허용), false(차단)
     */
    @Override
    @CircuitBreaker(name = "redisRateLimit", fallbackMethod = "fallbackForUser")
    public boolean isAllowedForUser(Long userId) {
        String key = userPrefix + userId;
        return rateLimitPort.isAllowed(key, userCapacity, userRefillRate);
    }

    // 파라미터를 원본 메서드(Long userId)에 맞춤!
    public boolean fallbackForUser(Long userId, Throwable t) {
        String key = userPrefix + userId; // 내부에서 Key 조립

        Integer currentCount = localRateLimitCache.asMap().compute(key, (k, count) -> {
            if (count == null) return 1;
            return count + 1;
        });

        if (currentCount > userCapacity) { // 필드에 저장된 userCapacity 사용
            log.warn("[L1 Local 방어막] 한도 초과 차단! Key: {}, 현재 요청 수: {}", key, currentCount);
            return false;
        }

        log.info("[Circuit Breaker] Redis 장애! 로컬 캐시로 요청 허용 ({} / {})", currentCount, userCapacity);
        return true;
    }

    /**
     * IP 주소 기반의 요청 허용 여부를 확인합니다.
     * @param ip 클라이언트 IP 주소
     * @return true(허용), false(차단)
     */
    @Override
    @CircuitBreaker(name = "redisRateLimit", fallbackMethod = "fallbackForIp")
    public boolean isAllowedForIp(String ip) {
        String key = ipPrefix + ip;
        return rateLimitPort.isAllowed(key, ipCapacity, ipRefillRate);
    }

    public boolean fallbackForIp(String ip, Throwable t) {
        log.error("[Circuit Breaker] Redis 장애 감지! IP {}의 요청을 무조건 허용(Fail-Open)합니다. 사유: {}", ip, t.getMessage());
        return true;
    }
}