package com.serverbe.application.service;

import com.serverbe.application.port.out.ratelimit.RateLimitPort;
import com.serverbe.application.service.fallback.RateLimitFallbackHandler;
import com.serverbe.application.config.RateLimitKeyPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    private RateLimiterService rateLimiterService;

    @Mock
    private RateLimitPort rateLimitPort;

    @Mock
    private RateLimitFallbackHandler fallbackHandler; // Cache 대신 Handler Mocking

    private static final String TEST_ENDPOINT = "/api/v1/test";

    @BeforeEach
    void setUp() {
        // 키 접두사는 순수 record라 mocking할 것이 없습니다. application.yml의 실제 값과 동일하게 둡니다.
        rateLimiterService = new RateLimiterService(
                rateLimitPort, new RateLimitKeyPolicy("rate:user:", "rate:ip:"), fallbackHandler);
    }

    @Test
    @DisplayName("User ID 기반 요청이 허용되면 true를 반환한다 (새로운 Key 조합 확인)")
    void isAllowedForUser_Allowed() {
        Long userId = 100L;
        String expectedKey = "rate:user:100:" + TEST_ENDPOINT;
        int capacity = 10;
        int refillRate = 1;

        given(rateLimitPort.isAllowed(expectedKey, capacity, refillRate)).willReturn(true);

        boolean result = rateLimiterService.isAllowedForUser(userId, TEST_ENDPOINT, capacity, refillRate);

        assertThat(result).isTrue();
        verify(rateLimitPort).isAllowed(expectedKey, capacity, refillRate);
    }

    @Test
    @DisplayName("IP 기반 요청이 차단되면 false를 반환한다 (새로운 Key 조합 확인)")
    void isAllowedForIp_Blocked() {
        String ip = "192.168.0.1";
        String expectedKey = "rate:ip:192.168.0.1:" + TEST_ENDPOINT;
        int capacity = 5;
        int refillRate = 1;

        given(rateLimitPort.isAllowed(expectedKey, capacity, refillRate)).willReturn(false);

        boolean result = rateLimiterService.isAllowedForIp(ip, TEST_ENDPOINT, capacity, refillRate);

        assertThat(result).isFalse();
        verify(rateLimitPort).isAllowed(expectedKey, capacity, refillRate);
    }

    @Test
    @DisplayName("User 요청 Redis 장애 시 FallbackHandler로 처리를 위임한다")
    void fallbackForUser_DelegatesToHandler() {
        Long userId = 100L;
        int capacity = 10;
        int refillRate = 1;
        Throwable dummyException = new RuntimeException("Redis Down");

        given(fallbackHandler.handleUserFallback(userId, TEST_ENDPOINT, capacity, refillRate, "rate:user:", dummyException))
                .willReturn(true);

        boolean result = rateLimiterService.fallbackForUser(userId, TEST_ENDPOINT, capacity, refillRate, dummyException);

        assertThat(result).isTrue();
        verify(fallbackHandler).handleUserFallback(userId, TEST_ENDPOINT, capacity, refillRate, "rate:user:", dummyException);
    }

    @Test
    @DisplayName("IP 요청 Redis 장애 시 FallbackHandler로 처리를 위임한다")
    void fallbackForIp_DelegatesToHandler() {
        String ip = "127.0.0.1";
        int capacity = 5;
        int refillRate = 1;
        Throwable dummyException = new RuntimeException("Redis Down");

        given(fallbackHandler.handleIpFallback(ip, TEST_ENDPOINT, capacity, refillRate, dummyException)).willReturn(true);

        boolean result = rateLimiterService.fallbackForIp(ip, TEST_ENDPOINT, capacity, refillRate, dummyException);

        assertThat(result).isTrue();
        verify(fallbackHandler).handleIpFallback(ip, TEST_ENDPOINT, capacity, refillRate, dummyException);
    }
}