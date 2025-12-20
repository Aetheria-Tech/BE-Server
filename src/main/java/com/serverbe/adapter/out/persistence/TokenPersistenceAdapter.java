package com.serverbe.adapter.out.persistence;

import com.serverbe.application.port.out.TokenPersistencePort;
import com.serverbe.infrastructure.config.properties.RedisProperties;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class TokenPersistenceAdapter implements TokenPersistencePort {

    private final RedisTemplate<String, Object> redisTemplate;
    private final int MAX_TOKEN;
    private final String RT_PREFIX;
    private final String RT_SUFFIX;
    private final String BL_PREFIX;
    private final String BL_SUFFIX;

    public TokenPersistenceAdapter(
            RedisTemplate<String, Object> redisTemplate,
            RedisProperties redisProperties) {
        this.redisTemplate = redisTemplate;
        this.MAX_TOKEN = redisProperties.auth().maxToken();
        this.RT_PREFIX = redisProperties.auth().prefix();
        this.RT_SUFFIX = redisProperties.auth().suffix();
        this.BL_PREFIX = redisProperties.blacklist().prefix();
        this.BL_SUFFIX = redisProperties.blacklist().suffix();
    }

    @Override
    public void saveRefreshToken(Long userId, String refreshToken, Duration expiry) {
        String key = createRefreshTokenKey(userId);

        // 1. 현재 저장된 리프레시 토큰 개수 확인
        Long currentSize = redisTemplate.opsForList().size(key);

        // 2. 최대 개수(max-token)를 초과하면 가장 오래된 토큰(왼쪽) 삭제 (FIFO)
        if (currentSize != null && currentSize >= MAX_TOKEN) {
            redisTemplate.opsForList().leftPop(key);
        }

        // 3. 새로운 토큰을 리스트의 오른쪽(최신)에 추가
        redisTemplate.opsForList().rightPush(key, refreshToken);

        // 4. 해당 리스트 전체에 대해 만료 시간 설정
        redisTemplate.expire(key, expiry);
    }

    @Override
    public boolean existsRefreshToken(Long userId, String refreshToken) {
        String key = createRefreshTokenKey(userId);
        List<Object> tokens = redisTemplate.opsForList().range(key, 0, -1);

        if (tokens == null) return false;
        return tokens.stream()
                .map(Object::toString)
                .anyMatch(storedToken -> storedToken.equals(refreshToken));
    }

    /**
     * 리스트 구조에 맞춰 단일 조회가 아닌 리스트 존재 여부 확인으로 대체하거나
     * 최신 토큰을 반환하도록 변경해야 합니다.
     */
    @Override
    public String getRefreshToken(Long userId) {
        String key = createRefreshTokenKey(userId);
        // 리스트의 가장 마지막(최신) 토큰 하나만 조회
        Object token = redisTemplate.opsForList().index(key, -1);
        return token != null ? token.toString() : null;
    }

    /**
     * 토큰 순환(RTR) 시 사용한 기존 토큰을 리스트에서 제거하기 위해 필요합니다.
     */
    @Override
    public void removeSpecificRefreshToken(Long userId, String refreshToken) {
        String key = createRefreshTokenKey(userId);
        // 리스트에서 일치하는 토큰 1개를 찾아 삭제
        redisTemplate.opsForList().remove(key, 1, refreshToken);
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