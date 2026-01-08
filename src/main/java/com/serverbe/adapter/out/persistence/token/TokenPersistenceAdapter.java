package com.serverbe.adapter.out.persistence.token;

import com.serverbe.application.port.out.token.TokenPersistencePort;
import com.serverbe.infrastructure.config.properties.RedisProperties;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * @author Duskafka
 * @responsibility Redis에서 토큰에 대한 정보를 관리하는 책임
 * @see TokenPersistencePort
 */
@Component
public class TokenPersistenceAdapter implements TokenPersistencePort {

    private final RedisTemplate<String, Object> redisTemplate;
    private final int maxToken;
    private final String rtPrefix;
    private final String rtSuffix;
    private final String blPrefix;
    private final String blSuffix;

    public TokenPersistenceAdapter(
            RedisTemplate<String, Object> redisTemplate,
            RedisProperties redisProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.maxToken = redisProperties.auth().maxToken();
        this.rtPrefix = redisProperties.auth().prefix();
        this.rtSuffix = redisProperties.auth().suffix();
        this.blPrefix = redisProperties.blacklist().prefix();
        this.blSuffix = redisProperties.blacklist().suffix();
    }

    /**
     * Redis에 리프레시 토큰을 저장하기 위해서 사용한다.
     *
     * @param userId       사용자 ID
     * @param refreshToken 저장할 리프레시 토큰
     * @param expiry       리프레시 토큰의 유효기간 (TTL로 저장하기 위해서 사용)
     * @responsibility Redis에 리프레시 토큰을 저장하는 책임
     * @implSpec 최대 리프레시 토큰 갯수를 초과하면 가장 오래된 토큰부터 삭제한다.
     * @see TokenPersistencePort#saveRefreshToken(Long, String, Duration)
     */
    @Override
    public void saveRefreshToken(Long userId, String refreshToken, Duration expiry) {
        String key = createRefreshTokenKey(userId);

        // 1. 현재 저장된 리프레시 토큰 개수 확인
        Long currentSize = redisTemplate.opsForList().size(key);

        // 2. 최대 개수(max-token)를 초과하면 가장 오래된 토큰(왼쪽) 삭제 (FIFO)
        if (currentSize != null && currentSize >= maxToken) {
            redisTemplate.opsForList().leftPop(key);
        }

        // 3. 새로운 토큰을 리스트의 오른쪽(최신)에 추가
        redisTemplate.opsForList().rightPush(key, refreshToken);

        // 4. 해당 리스트 전체에 대해 만료 시간 설정
        redisTemplate.expire(key, expiry);
    }

    /**
     * Redis에 리프레시 토큰이 존재하는지 확인하기 위하여 사용한다.
     *
     * @param refreshToken 존재하는지 확인할 리프레시 토큰
     * @param userId       사용자 ID
     * @return 존재하면 true 존재하지 않으면 false, 만약 일치하는 것이 없아도 false
     * @responsibility Redis에 사용자의 리프레시 토큰이 존재하는지 확인하는 책임
     * @implSpec 토큰이 List 형태로 저장되기 때문에 opsForList를 사용해야 한다. 0과 -1은 전체를 조회한다는 의미이다.
     * @see TokenPersistencePort#existsRefreshToken(Long, String)
     */
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
     * 리프레시 토큰을 조회하기 위하여 사용한다.
     *
     * @param userId 사용자 ID
     * @return 조회한 가장 최신 토큰
     * @deprecated
     */
    @Override
    public String getRefreshToken(Long userId) {
        String key = createRefreshTokenKey(userId);
        // 리스트의 가장 마지막(최신) 토큰 하나만 조회
        Object token = redisTemplate.opsForList().index(key, -1);
        return token != null ? token.toString() : null;
    }

    /**
     * 특정 리프레시 토큰을 삭제하기 위해 사용한다.
     *
     * @param refreshToken 삭제할 리프레시 토큰
     * @param userId       사용자 ID
     * @implNote 토큰 순환(RTR) 시 사용한 기존 토큰을 리스트에서 제거하기 위해 필요하다.
     * @implSpec Redis의 List 형태에서 일치하는 토큰 1개만 찾아 삭제한다.
     * @responsibility 특정한 토큰을 찾아서 삭제하는 책임
     * @see TokenPersistencePort#removeSpecificRefreshToken(Long, String)
     */
    @Override
    public void removeSpecificRefreshToken(Long userId, String refreshToken) {
        String key = createRefreshTokenKey(userId);
        // 리스트에서 일치하는 토큰 1개를 찾아 삭제
        redisTemplate.opsForList().remove(key, 1, refreshToken);
    }

    /**
     * 사용자의 모든 리프레시 토큰을 삭제하는 메소드
     *
     * @param userId 사용자 ID
     * @implSpec Redis에서 사용자 ID와 일치하는 모든 리프레시 토큰을 무효화한다.
     * @responsibility Key값에 일치하는 모든 Value(리프레시 토큰)을 삭제하는 책임.
     * @see TokenPersistencePort#deleteRefreshToken(Long)
     */
    @Override
    public void deleteRefreshToken(Long userId) {
        redisTemplate.delete(createRefreshTokenKey(userId));
    }

    /**
     * 토큰을 새로 발급할 때 사용한 액세스 토큰을 Redis에 블랙리스트로 등록하는 메소드.
     *
     * @param accessToken   블랙리스트에 등록할 액세스 토큰
     * @param remainingTime 액세스 토큰의 남은 시간
     * @implNote 만약 블랙리스트에 등록된 액세스 토큰이 재사용되면 해킹을 의심할 수 있다.
     * @implSpec 혹시 사용자가 미리 요청을 했을 수 있으니 만료 시간을 액세스 토큰의 남은 시간으로 설정한다.
     * @responsibility 액세스 토큰을 Redis의 블랙리스트에 등록한다.
     * @see TokenPersistencePort#blacklistAccessToken(String, Duration)
     */
    @Override
    public void blacklistAccessToken(String accessToken, Duration remainingTime) {
        redisTemplate.opsForValue().set(createBlacklistKey(accessToken), "logout", remainingTime);
    }

    /**
     * 블랙리스트에 등록되었는지 확인하기 위한 메소드.
     *
     * @param accessToken 블랙리스트에 등록되었는지 확인할 액세스 토큰
     * @return 블랙리스트에 등록되었으면 true, 아니면 false
     * @responsibility 액세스 토큰이 블랙리스트에 등록되었는지 확인하는 책임.
     * @see TokenPersistencePort#isBlacklisted(String)
     */
    @Override
    public boolean isBlacklisted(String accessToken) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(createBlacklistKey(accessToken)));
    }

    /**
     * 리프레시 토큰 키를 조합하기 위한 메소드
     *
     * @param userId 사용자 ID
     * @return 조합된 리프레시 토큰 조회 키
     */
    private String createRefreshTokenKey(Long userId) {
        return String.format("%s:%s:%s",
                rtPrefix,
                userId,
                rtSuffix
        );
    }

    /**
     * 액세스 토큰 블랙리스트 조회 키를 조합하기 위한 메소드
     *
     * @param accessToken 조회할 액세스 토큰
     * @return 조합된 블랙리스트 조회 키
     */
    private String createBlacklistKey(String accessToken) {
        return String.format("%s:%s:%s",
                blPrefix,
                accessToken,
                blSuffix);
    }
}