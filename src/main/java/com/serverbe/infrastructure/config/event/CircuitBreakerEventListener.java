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
    private final NotificationService notificationService;

    @PostConstruct
    public void registerEventListener() {
        // 1. 현재 레지스트리에 등록된 모든 서킷 브레이커에 리스너 등록
        circuitBreakerRegistry.getAllCircuitBreakers()
                .forEach(this::addStateTransitionListener);

        // 2. (안전장치) 애플리케이션 실행 후 지연 생성(Lazy Init)되는 서킷 브레이커에도 리스너 등록
        circuitBreakerRegistry.getEventPublisher()
                .onEntryAdded(entryAddedEvent -> addStateTransitionListener(entryAddedEvent.getAddedEntry()));
    }

    private void addStateTransitionListener(CircuitBreaker circuitBreaker) {
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> {
                    String cbName = circuitBreaker.getName();
                    CircuitBreaker.State toState = event.getStateTransition().getToState();

                    if (toState == CircuitBreaker.State.CLOSED) {
                        handleClosedState(cbName);
                    } else if (toState == CircuitBreaker.State.OPEN) {
                        handleOpenState(cbName);
                    }
                });
    }

    private void handleClosedState(String cbName) {
        log.info("[{}] 서킷 브레이커 복구 완료! (CLOSED)", cbName);

        // Redis Rate Limit 전용 로직
        if ("redisRateLimit".equals(cbName)) {
            log.info("[L1 Cache] Redis 복구 완료! 로컬 방어막(L1) 초기화.");
            localRateLimitCache.invalidateAll();
        }

        notificationService.sendDiscordNotification(
                String.format("🟢 **[복구 알림]** `%s` 연결이 복구되어 서킷 브레이커가 CLOSED 되었습니다. 시스템이 정상화되었습니다.", cbName)
        );
    }

    private void handleOpenState(String cbName) {
        log.error("🚨 [{}] 장애 발생! 서킷 브레이커가 OPEN 되었습니다!", cbName);

        // Redis Rate Limit 전용 추가 메시지
        String extraMessage = "redisRateLimit".equals(cbName) ? " L1 로컬 방어막이 가동됩니다. " : " ";

        notificationService.sendDiscordNotification(
                String.format("🔴 **[장애 알림]** `%s` 장애 발생으로 서킷 브레이커가 OPEN 되었습니다!%s확인이 필요합니다!", cbName, extraMessage)
        );
    }
}