package com.serverbe.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Boolean> rateLimitScript;

    // 정책 상수 (필요에 따라 프로퍼티나 DB로 관리 가능)
    private static final int USER_CAPACITY = 10;
    private static final int USER_REFILL_RATE = 10; // 초당 10개
    
    private static final int IP_CAPACITY = 5;
    private static final int IP_REFILL_RATE = 5;    // 초당 5개

    public boolean isAllowedForUser(Long userId) {
        String key = "rate:user:" + userId;
        return executeScript(key, USER_CAPACITY, USER_REFILL_RATE);
    }

    public boolean isAllowedForIp(String ip) {
        String key = "rate:ip:" + ip;
        return executeScript(key, IP_CAPACITY, IP_REFILL_RATE);
    }

    /**
     * @implSpec StringRedisTemplate을 쓰므로 모든 인자를 String으로 명확히 전달
     * */
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