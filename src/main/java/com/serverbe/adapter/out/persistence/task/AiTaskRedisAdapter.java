package com.serverbe.adapter.out.persistence.task;

import com.serverbe.application.port.out.task.TaskRateLimitPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class AiTaskRedisAdapter implements TaskRateLimitPort {

    private final StringRedisTemplate redisTemplate;
    private static final String LOCK_KEY_PREFIX = "ai:task:lock:";

    @Override
    public boolean tryLock(Long userId, int seconds) {
        String key = LOCK_KEY_PREFIX + userId;
        // SETNX + EXPIRE를 원자적으로 실행
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, "locked", Duration.ofSeconds(seconds));
        
        return Boolean.TRUE.equals(success);
    }
}