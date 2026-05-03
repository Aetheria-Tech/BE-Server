package com.serverbe.application.service.fallback;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFallbackHandler {

    private final Cache<String, Integer> localRateLimitCache;

    public boolean handleUserFallback(Long userId, String endpoint, int capacity, int refillRate, String userPrefix, Throwable t) {
        String key = userPrefix + userId + ":" + endpoint;
        Integer currentCount = localRateLimitCache.asMap().compute(key, (k, count) -> {
            if (count == null) return 1;
            return count + 1;
        });

        if (currentCount > capacity) {
            log.warn("[L1 Local 방어막] 한도 초과 차단! Key: {}, 현재 요청 수: {}", key, currentCount);
            return false;
        }
        log.info("[Circuit Breaker] Redis 장애! 로컬 캐시로 요청 허용 ({} / {}) - 사유: {}", currentCount, capacity, t.getMessage());
        return true;
    }

    public boolean handleIpFallback(String ip, String endpoint, int ipCapacity, int ipRefillRate, Throwable t) {
        log.error("[Circuit Breaker] Redis 장애 감지! IP {}의 요청을 무조건 허용(Fail-Open). 사유: {}", ip, t.getMessage());
        return true;
    }
}