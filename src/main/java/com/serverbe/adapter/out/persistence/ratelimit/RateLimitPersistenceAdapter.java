package com.serverbe.adapter.out.persistence.ratelimit;

import com.serverbe.application.port.out.ratelimit.RateLimitPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class RateLimitPersistenceAdapter implements RateLimitPort {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Boolean> rateLimitScript;

    public RateLimitPersistenceAdapter(
            StringRedisTemplate redisTemplate,
            @Qualifier("rateLimitScript") RedisScript<Boolean> rateLimitScript
    ) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = rateLimitScript;
    }

    @Override
    public boolean isAllowed(String key, int capacity, int refillRate) {
        List<String> keys = Collections.singletonList(key);

        try {
            return Boolean.TRUE.equals(redisTemplate.execute(
                    rateLimitScript,
                    keys,
                    String.valueOf(capacity),
                    String.valueOf(refillRate),
                    "1", // requested tokens (현재는 1로 고정)
                    String.valueOf(System.currentTimeMillis())
            ));
        } catch (Exception e) {
            log.error("Rate Limit Redis Error: {}", e.getMessage());
            // Redis 장애 시 일단 허용할지, 차단할지 결정 (일반적으로는 서비스 가용성을 위해 true 반환)
            throw e;
        }
    }
}