package com.serverbe.application.service;

import com.serverbe.application.port.out.ratelimit.RateLimitPort;
import com.serverbe.infrastructure.config.properties.RateLimitProperties;
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
    private RateLimitProperties rateLimitProperties;
    @Mock
    private RateLimitProperties.User userProperties;
    @Mock
    private RateLimitProperties.Ip ipProperties;
    @Mock
    private RateLimitProperties.Prefix prefixProperties;

    @BeforeEach
    void setUp() {
        // 2. Properties 내부 값 Stubbing
        // (User 설정: 10개 용량, 10 리필)
        given(rateLimitProperties.user()).willReturn(userProperties);
        given(userProperties.capacity()).willReturn(10);
        given(userProperties.refillRate()).willReturn(10);

        // (IP 설정: 5개 용량, 5 리필)
        given(rateLimitProperties.ip()).willReturn(ipProperties);
        given(ipProperties.capacity()).willReturn(5);
        given(ipProperties.refillRate()).willReturn(5);

        // (Prefix 설정)
        given(rateLimitProperties.prefix()).willReturn(prefixProperties);
        given(prefixProperties.user()).willReturn("rate:user:");
        given(prefixProperties.ip()).willReturn("rate:ip:");

        // 3. 서비스 생성 (Redis 관련 의존성 대신 Port 주입)
        rateLimiterService = new RateLimiterService(rateLimitPort, rateLimitProperties);
    }

    @Test
    @DisplayName("User ID 기반 요청이 허용되면 true를 반환한다")
    void isAllowedForUser_Allowed() {
        // given
        Long userId = 100L;
        String expectedKey = "rate:user:100";
        int capacity = 10;
        int refillRate = 10;

        // Port가 true를 반환하도록 Stubbing
        given(rateLimitPort.isAllowed(expectedKey, capacity, refillRate))
                .willReturn(true);

        // when
        boolean result = rateLimiterService.isAllowedForUser(userId);

        // then
        assertThat(result).isTrue();

        // 검증: 서비스가 Port에게 올바른 키와 설정값을 넘겼는지 확인
        verify(rateLimitPort).isAllowed(expectedKey, capacity, refillRate);
    }

    @Test
    @DisplayName("IP 기반 요청이 차단되면 false를 반환한다")
    void isAllowedForIp_Blocked() {
        // given
        String ip = "192.168.0.1";
        String expectedKey = "rate:ip:192.168.0.1";
        int capacity = 5;
        int refillRate = 5;

        // Port가 false를 반환하도록 Stubbing
        given(rateLimitPort.isAllowed(expectedKey, capacity, refillRate))
                .willReturn(false);

        // when
        boolean result = rateLimiterService.isAllowedForIp(ip);

        // then
        assertThat(result).isFalse();

        // 검증: 서비스가 Port에게 올바른 키와 설정값을 넘겼는지 확인
        verify(rateLimitPort).isAllowed(expectedKey, capacity, refillRate);
    }
}