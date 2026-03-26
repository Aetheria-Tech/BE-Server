package com.serverbe.infrastructure.config.event;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CircuitBreakerEventListenerTest {

    private CircuitBreakerEventListener eventListener;

    // Resilience4j 레지스트리는 Mocking하지 않고 실제 객체를 사용합니다. (이벤트 발행을 위해)
    private CircuitBreakerRegistry circuitBreakerRegistry;

    // 초기화 여부를 검증할 캐시만 Mock 객체로 만듭니다.
    @Mock
    private Cache<String, Integer> localRateLimitCache;

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        // 1. 실제 Registry 생성
        circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();

        // 2. 리스너 객체 생성 및 의존성 주입
        eventListener = new CircuitBreakerEventListener(circuitBreakerRegistry, localRateLimitCache);

        // 3. @PostConstruct 메서드 수동 호출 (이벤트 구독 시작)
        eventListener.registerEventListener();

        // 4. 상태 조작을 위해 서킷 브레이커 인스턴스 가져오기
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("redisRateLimit");
    }

    @Test
    @DisplayName("서킷 브레이커가 CLOSED(복구) 상태로 전환되면 L1 캐시를 전체 초기화한다")
    void shouldInvalidateCache_whenStateTransitionsToClosed() {
        // given: 초기 상태는 CLOSED이므로, 이벤트를 발생시키기 위해 먼저 OPEN 상태로 만듭니다.
        circuitBreaker.transitionToOpenState();

        // OPEN으로 변했을 때는 초기화(invalidateAll)가 호출되지 않아야 합니다.
        verify(localRateLimitCache, never()).invalidateAll();

        // when: 서킷 브레이커 상태를 CLOSED(정상 복구)로 강제 전환합니다.
        circuitBreaker.transitionToClosedState();

        // then: 캐시 전체 초기화 메서드가 정확히 1번 호출되었는지 검증합니다.
        verify(localRateLimitCache, times(1)).invalidateAll();
    }

    @Test
    @DisplayName("서킷 브레이커가 OPEN(장애) 상태로 전환될 때는 캐시를 초기화하지 않는다")
    void shouldNotInvalidateCache_whenStateTransitionsToOpen() {
        // when: 서킷 브레이커 상태를 OPEN(장애)으로 강제 전환합니다.
        circuitBreaker.transitionToOpenState();

        // then: 장애 발생 시점에는 캐시가 유지되어야 하므로 초기화 메서드가 호출되지 않아야 합니다.
        verify(localRateLimitCache, never()).invalidateAll();
    }

    @Test
    @DisplayName("서킷 브레이커가 HALF_OPEN(반오픈) 상태로 전환될 때는 캐시를 초기화하지 않는다")
    void shouldNotInvalidateCache_whenStateTransitionsToHalfOpen() {
        // given: HALF_OPEN으로 가려면 먼저 OPEN 상태여야 합니다.
        circuitBreaker.transitionToOpenState();

        // when: 서킷 브레이커 상태를 HALF_OPEN으로 강제 전환합니다.
        circuitBreaker.transitionToHalfOpenState();

        // then: 완전히 복구된 것(CLOSED)이 아니므로 캐시를 초기화하지 않아야 합니다.
        verify(localRateLimitCache, never()).invalidateAll();
    }
}