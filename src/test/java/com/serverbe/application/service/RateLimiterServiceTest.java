package com.serverbe.application.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.serverbe.application.port.out.ratelimit.RateLimitPort;
import com.serverbe.infrastructure.config.properties.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    private RateLimiterService rateLimiterService;

    @Mock
    private RateLimitPort rateLimitPort;

    @Mock
    private RateLimitProperties rateLimitProperties;
    @Mock
    private RateLimitProperties.User userProperties;
    @Mock
    private RateLimitProperties.Ip ipProperties;
    @Mock
    private RateLimitProperties.Prefix prefixProperties;

    @Mock
    private Cache<String, Integer> localRateLimitCache; // 캐시 Mock 추가

    // Caffeine의 asMap() 동작을 시뮬레이션하기 위한 실제 Map
    private ConcurrentMap<String, Integer> fakeCacheMap;

    @BeforeEach
    void setUp() {
        given(rateLimitProperties.user()).willReturn(userProperties);
        given(userProperties.capacity()).willReturn(10);
        given(userProperties.refillRate()).willReturn(10);

        given(rateLimitProperties.ip()).willReturn(ipProperties);
        given(ipProperties.capacity()).willReturn(5);
        given(ipProperties.refillRate()).willReturn(5);

        given(rateLimitProperties.prefix()).willReturn(prefixProperties);
        given(prefixProperties.user()).willReturn("rate:user:");
        given(prefixProperties.ip()).willReturn("rate:ip:");

        fakeCacheMap = new ConcurrentHashMap<>();

        // 3번째 인자로 Mock 캐시 주입 (오류 해결!)
        rateLimiterService = new RateLimiterService(rateLimitPort, rateLimitProperties, localRateLimitCache);
    }

    @Test
    @DisplayName("User ID 기반 요청이 허용되면 true를 반환한다")
    void isAllowedForUser_Allowed() {
        Long userId = 100L;
        String expectedKey = "rate:user:100";
        int capacity = 10;
        int refillRate = 10;

        given(rateLimitPort.isAllowed(expectedKey, capacity, refillRate)).willReturn(true);

        boolean result = rateLimiterService.isAllowedForUser(userId);

        assertThat(result).isTrue();
        verify(rateLimitPort).isAllowed(expectedKey, capacity, refillRate);
    }

    @Test
    @DisplayName("IP 기반 요청이 차단되면 false를 반환한다")
    void isAllowedForIp_Blocked() {
        String ip = "192.168.0.1";
        String expectedKey = "rate:ip:192.168.0.1";
        int capacity = 5;
        int refillRate = 5;

        given(rateLimitPort.isAllowed(expectedKey, capacity, refillRate)).willReturn(false);

        boolean result = rateLimiterService.isAllowedForIp(ip);

        assertThat(result).isFalse();
        verify(rateLimitPort).isAllowed(expectedKey, capacity, refillRate);
    }

    @Test
    @DisplayName("Redis 장애 시 Fallback: L1 캐시 용량을 초과하지 않으면 허용(true)한다")
    void fallbackForUser_UnderCapacity() {
        // given: Caffeine 캐시의 asMap()이 우리의 fakeMap을 반환하도록 설정
        given(localRateLimitCache.asMap()).willReturn(fakeCacheMap);
        Long userId = 100L;
        Throwable dummyException = new RuntimeException("Redis 죽음");

        // when: 용량(10) 이하인 첫 번째 호출
        boolean result = rateLimiterService.fallbackForUser(userId, dummyException);

        // then
        assertThat(result).isTrue();
        assertThat(fakeCacheMap.get("rate:user:100")).isEqualTo(1);
    }

    @Test
    @DisplayName("Redis 장애 시 Fallback: L1 캐시 용량을 초과하면 차단(false)한다")
    void fallbackForUser_OverCapacity() {
        // given
        given(localRateLimitCache.asMap()).willReturn(fakeCacheMap);
        Long userId = 100L;
        String key = "rate:user:100";
        Throwable dummyException = new RuntimeException("Redis 죽음");

        // 이미 캐시에 용량(10)만큼 요청이 쌓여있다고 가정
        fakeCacheMap.put(key, 10);

        // when: 11번째 호출 시도
        boolean result = rateLimiterService.fallbackForUser(userId, dummyException);

        // then: L1 방어막이 차단(false)해야 함
        assertThat(result).isFalse();
        assertThat(fakeCacheMap.get(key)).isEqualTo(11);
    }

    @Test
    @DisplayName("[L1 방어막] Redis 장애 시 첫 요청은 카운트를 1로 초기화하고 허용(true)한다")
    void fallbackForUser_FirstRequest_ShouldAllow() {
        // given
        given(localRateLimitCache.asMap()).willReturn(fakeCacheMap);
        Long userId = 100L;
        String expectedKey = "rate:user:100";
        Throwable redisException = new RuntimeException("Redis Timeout");

        // when
        boolean result = rateLimiterService.fallbackForUser(userId, redisException);

        // then
        assertThat(result).isTrue(); // 요청 허용
        assertThat(fakeCacheMap.get(expectedKey)).isEqualTo(1); // 캐시에 1로 저장됨
    }

    @Test
    @DisplayName("[L1 방어막] 한도(10) 도달 전까지는 카운트를 누적하며 허용(true)한다")
    void fallbackForUser_UnderCapacity_ShouldAllow() {
        // given
        given(localRateLimitCache.asMap()).willReturn(fakeCacheMap);
        Long userId = 100L;
        String expectedKey = "rate:user:100";
        Throwable redisException = new RuntimeException("Redis Connection Refused");

        // 캐시에 이미 9번 요청한 상태를 세팅
        fakeCacheMap.put(expectedKey, 9);

        // when (10번째 요청 시도)
        boolean result = rateLimiterService.fallbackForUser(userId, redisException);

        // then
        assertThat(result).isTrue(); // 10번째까지는 허용
        assertThat(fakeCacheMap.get(expectedKey)).isEqualTo(10); // 카운트 10으로 증가
    }

    @Test
    @DisplayName("[L1 방어막] 설정된 한도(10)를 초과하면 L1 캐시가 자체적으로 차단(false)한다")
    void fallbackForUser_ExceedsCapacity_ShouldBlock() {
        // given
        given(localRateLimitCache.asMap()).willReturn(fakeCacheMap);
        Long userId = 100L;
        String expectedKey = "rate:user:100";
        Throwable redisException = new RuntimeException("Redis Down");

        // 캐시에 이미 한도치인 10번 요청이 꽉 찬 상태를 세팅
        fakeCacheMap.put(expectedKey, 10);

        // when (11번째 요청 시도)
        boolean result = rateLimiterService.fallbackForUser(userId, redisException);

        // then
        assertThat(result).isFalse(); // 11번째부터는 L1 방어막에 의해 차단!
        assertThat(fakeCacheMap.get(expectedKey)).isEqualTo(11); // 카운트는 증가함
    }

    @Test
    @DisplayName("[L1 방어막] IP 기반 Fallback은 L1 캐시 검사 없이 무조건 허용(true)한다")
    void fallbackForIp_ShouldAlwaysAllow() {
        // given
        String ip = "127.0.0.1";
        Throwable redisException = new RuntimeException("Redis Down");

        // when
        boolean result = rateLimiterService.fallbackForIp(ip, redisException);

        // then
        assertThat(result).isTrue(); // 무조건 통과 (Fail-Open)

        // IP Fallback은 localRateLimitCache를 사용하지 않으므로 상호작용이 없어야 함
        // (필요 시 Mockito.verifyNoInteractions(localRateLimitCache) 로 검증 가능)
    }
}