package com.serverbe.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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

    @InjectMocks
    private RateLimiterService rateLimiterService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RedisScript<Boolean> rateLimitScript;

    @Test
    @DisplayName("User ID 기반 요청이 허용되면 true를 반환한다")
    void isAllowedForUser_Allowed() {
        // given
        Long userId = 100L;
        // execute(script, keys, args...) 호출 시 true 반환하도록 Mock 설정
        given(redisTemplate.execute(
                eq(rateLimitScript),
                anyList(), // keys
                any(String.class), any(String.class), any(String.class), any(String.class) // args
        )).willReturn(true);

        // when
        boolean result = rateLimiterService.isAllowedForUser(userId);

        // then
        assertThat(result).isTrue();
        
        // Redis Key가 올바르게 생성되어 전달되었는지 검증
        verify(redisTemplate).execute(
                eq(rateLimitScript),
                argThat((List<String> keys) -> keys.get(0).equals("rate:user:100")),
                any(), any(), any(), any()
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
        
        verify(redisTemplate).execute(
                eq(rateLimitScript),
                argThat((List<String> keys) -> keys.get(0).equals("rate:ip:192.168.0.1")),
                any(), any(), any(), any()
        );
    }
}