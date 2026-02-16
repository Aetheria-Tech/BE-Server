package com.serverbe.application.service;

import com.serverbe.infrastructure.config.properties.LimitRateProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    // @InjectMocks 제거 (생성자 직접 호출을 위해)
    private RateLimiterService rateLimiterService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RedisScript<Boolean> rateLimitScript;

    // 설정 클래스 및 내부 레코드 Mocking
    @Mock
    private LimitRateProperties limitRateProperties;
    @Mock
    private LimitRateProperties.User userProperties;
    @Mock
    private LimitRateProperties.Ip ipProperties;
    @Mock
    private LimitRateProperties.Prefix prefixProperties;

    @BeforeEach
    void setUp() {
        // 1. Properties 내부 값 Stubbing (User 설정)
        given(limitRateProperties.user()).willReturn(userProperties);
        given(userProperties.capacity()).willReturn(10);
        given(userProperties.refillRate()).willReturn(10);

        // 2. Properties 내부 값 Stubbing (IP 설정)
        given(limitRateProperties.ip()).willReturn(ipProperties);
        given(ipProperties.capacity()).willReturn(5);
        given(ipProperties.refillRate()).willReturn(5);

        // 3. Properties 내부 값 Stubbing (Prefix 설정)
        given(limitRateProperties.prefix()).willReturn(prefixProperties);
        given(prefixProperties.user()).willReturn("rate:user:");
        given(prefixProperties.ip()).willReturn("rate:ip:");

        // 4. 서비스 수동 생성 (의존성 주입)
        rateLimiterService = new RateLimiterService(redisTemplate, rateLimitScript, limitRateProperties);
    }

    @Test
    @DisplayName("User ID 기반 요청이 허용되면 true를 반환한다")
    void isAllowedForUser_Allowed() {
        // given
        Long userId = 100L;

        // Redis 실행 결과 Stubbing
        given(redisTemplate.execute(
                eq(rateLimitScript),
                anyList(),
                any(), any(), any(), any()
        )).willReturn(true);

        // when
        boolean result = rateLimiterService.isAllowedForUser(userId);

        // then
        assertThat(result).isTrue();

        // 검증: 설정값(Capacity: 10, Refill: 10)이 스크립트로 제대로 전달되었는지 확인
        verify(redisTemplate).execute(
                eq(rateLimitScript),
                argThat((List<String> keys) -> keys.get(0).equals("rate:user:100")), // Key 확인
                eq("10"), // capacity
                eq("10"), // refillRate
                eq("1"),  // requested tokens
                any()     // timestamp
        );
    }

    @Test
    @DisplayName("IP 기반 요청이 차단되면 false를 반환한다")
    void isAllowedForIp_Blocked() {
        // given
        String ip = "192.168.0.1";

        given(redisTemplate.execute(
                eq(rateLimitScript),
                anyList(),
                any(), any(), any(), any()
        )).willReturn(false);

        // when
        boolean result = rateLimiterService.isAllowedForIp(ip);

        // then
        assertThat(result).isFalse();

        // 검증: 설정값(Capacity: 5, Refill: 5)이 스크립트로 제대로 전달되었는지 확인
        verify(redisTemplate).execute(
                eq(rateLimitScript),
                argThat((List<String> keys) -> keys.get(0).equals("rate:ip:192.168.0.1")), // Key 확인
                eq("5"), // capacity
                eq("5"), // refillRate
                eq("1"), // requested tokens
                any()    // timestamp
        );
    }
}