package com.serverbe.infrastructure.config.event;

import com.github.benmanes.caffeine.cache.Cache;
import com.serverbe.application.service.NotificationService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CircuitBreakerEventListener {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final Cache<String, Integer> localRateLimitCache;
    // 알림 서비스 주입
    private final NotificationService notificationService;

    @PostConstruct
    public void registerEventListener() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("redisRateLimit");

        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> {
                    CircuitBreaker.State toState = event.getStateTransition().getToState();

                    // 1. 정상 복구 (CLOSED) 될 때
                    if (toState == CircuitBreaker.State.CLOSED) {
                        log.info("[L1 Cache] Redis 복구 완료! 로컬 방어막(L1) 초기화.");
                        localRateLimitCache.invalidateAll();

                        notificationService.sendDiscordNotification(
                                "🟢 **[복구 알림]** Redis 연결이 복구되어 서킷 브레이커가 CLOSED 되었습니다. 시스템이 정상화되었습니다."
                        );
                    }

                    // 2. 장애 발생 (OPEN) 할 때
                    if (toState == CircuitBreaker.State.OPEN) {
                        log.error("🚨 [장애 알림] Redis 연결 실패로 서킷 브레이커가 OPEN 되었습니다!");

                        notificationService.sendDiscordNotification(
                                "🔴 **[장애 알림]** Redis 연결 실패로 서킷 브레이커가 OPEN 되었습니다! L1 로컬 방어막이 가동됩니다. 확인이 필요합니다!"
                        );
                    }
                });
    }
}