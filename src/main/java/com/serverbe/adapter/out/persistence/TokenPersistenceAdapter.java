package com.serverbe.adapter.out.persistence;

import com.serverbe.application.port.out.TokenPersistencePort;
import com.serverbe.infrastructure.config.properties.RedisProperties;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class TokenPersistenceAdapter implements TokenPersistencePort {

    private final RedisTemplate<String, Object> redisTemplate;
    private final String RT_PREFIX;
    private final String RT_SUFFIX;
    private final String BL_PREFIX;
    private final String BL_SUFFIX;

    public TokenPersistenceAdapter(RedisTemplate<String, Object> redisTemplate, RedisProperties redisProperties) {
        this.redisTemplate = redisTemplate;
        this.RT_PREFIX = redisProperties.auth().prefix();
        this.RT_SUFFIX = redisProperties.auth().suffix();
        this.BL_PREFIX = redisProperties.blacklist().prefix();
        this.BL_SUFFIX = redisProperties.blacklist().suffix();
    }

    @Override
    public void saveRefreshToken(Long userId, String refreshToken, Duration expiry) {
        // 키 예: user:123:rt
        String key = createRefreshTokenKey(userId);
        redisTemplate.opsForValue().set(key, refreshToken, expiry);
    }

    @Override
    public void deleteRefreshToken(Long userId) {
        redisTemplate.delete(createRefreshTokenKey(userId));
    }

    @Override
    public void blacklistAccessToken(String accessToken, Duration remainingTime) {
        redisTemplate.opsForValue().set(createBlacklistKey(accessToken), "logout", remainingTime);
    }

    @Override
    public boolean isBlacklisted(String accessToken) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(createBlacklistKey(accessToken)));
    }

    private String createRefreshTokenKey(Long userId) {
        return String.format("%s:%s:%s",
                RT_PREFIX,
                userId,
                RT_SUFFIX
        );
    }

    private String createBlacklistKey(String accessToken) {
        return String.format("%s:%s:%s",
                BL_PREFIX,
                accessToken,
                BL_SUFFIX);
    }
}