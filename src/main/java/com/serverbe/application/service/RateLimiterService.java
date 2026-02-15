package com.serverbe.application.service;

import com.serverbe.infrastructure.config.properties.LimitRateProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Boolean> rateLimitScript;

    private final int userCapacity;
    private final int userRefillRate;
    private final int ipCapacity;
    private final int ipRefillRate;
    private final String userPrefix;
    private final String ipPrefix;

    public RateLimiterService(
            StringRedisTemplate redisTemplate,
            RedisScript<Boolean> rateLimitScript,
            LimitRateProperties limitRateProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = rateLimitScript;
        this.userCapacity = limitRateProperties.user().capacity();
        this.userRefillRate = limitRateProperties.user().refillRate();
        this.ipCapacity = limitRateProperties.ip().capacity();
        this.ipRefillRate = limitRateProperties.ip().refillRate();
        this.userPrefix = limitRateProperties.prefix().user();
        this.ipPrefix = limitRateProperties.prefix().ip();
    }

    /**
     * 인증된 사용자 ID 기반의 요청 허용 여부 확인
     * Key 예시: "rate:user:1" (prefix 설정에 따라 다름)
     */
    public boolean isAllowedForUser(Long userId) {
        String key = userPrefix + userId;
        return executeScript(key, userCapacity, userRefillRate);
    }

    /**
     * IP 주소 기반의 요청 허용 여부 확인
     * Key 예시: "rate:ip:127.0.0.1"
     */
    public boolean isAllowedForIp(String ip) {
        String key = ipPrefix + ip;
        return executeScript(key, ipCapacity, ipRefillRate);
    }

    /**
     * 실제 Redis Lua 스크립트를 실행하는 핵심 메서드
     * * @param key Redis에 저장될 Key
     * @param capacity 버킷의 최대 크기 (최대 허용 Burst)
     * @param refillRate 초당 토큰 충전 속도
     * @return true(허용), false(차단)
     */
    private boolean executeScript(String key, int capacity, int refillRate) {
        List<String> keys = Collections.singletonList(key);

        return Boolean.TRUE.equals(redisTemplate.execute(
                rateLimitScript,
                keys,
                String.valueOf(capacity),
                String.valueOf(refillRate),
                "1", // requested tokens
                String.valueOf(System.currentTimeMillis())
        ));
    }
}