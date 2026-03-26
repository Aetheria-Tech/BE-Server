package com.serverbe.infrastructure.config.event;

import com.github.benmanes.caffeine.cache.Cache;
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

    // 1. 서킷 브레이커들을 관리하는 레지스트리
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    // 2. 비워줄 L1 로컬 캐시
    private final Cache<String, Integer> localRateLimitCache; 

    @PostConstruct
    public void registerEventListener() {
        // 우리가 설정한 "redisRateLimit" 서킷 브레이커 객체를 가져옵니다.
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("redisRateLimit");

        // 상태 전환(State Transition) 이벤트를 구독합니다.
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> {
                    CircuitBreaker.State fromState = event.getStateTransition().getFromState();
                    CircuitBreaker.State toState = event.getStateTransition().getToState();

                    log.info("[Circuit Breaker] 상태 변경 감지: {} -> {}", fromState, toState);

                    // 상태가 CLOSED(정상 복구)로 변환되었을 때 로컬 캐시 초기화
                    if (toState == CircuitBreaker.State.CLOSED) {
                        log.info("[L1 Cache] Redis 복구 완료! 오래된 로컬 방어막(L1) 데이터를 모두 초기화합니다.");
                        localRateLimitCache.invalidateAll();
                    }
                    
                    // (선택) 장애 발생 시 알림을 넣기 좋은 위치
                    if (toState == CircuitBreaker.State.OPEN) {
                        log.error("🚨 [장애 알림] Redis 연결 실패로 서킷 브레이커가 OPEN 되었습니다! L1 방어막 가동!");
                        // TODO: Slack, Discord 알림 연동
                    }
                });
    }
}