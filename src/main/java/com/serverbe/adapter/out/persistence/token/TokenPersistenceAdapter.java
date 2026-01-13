package com.serverbe.adapter.out.persistence.token;

import com.serverbe.application.port.out.token.TokenPersistencePort;
import com.serverbe.infrastructure.config.properties.RedisProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Duskafka
 * @responsibility Redis를 활용하여 멀티 디바이스 환경의 토큰 영속성을 관리합니다.
 * @implSpec
 * 1. <b>Token Data</b>: Key-Value(String) 구조로 저장하며, 각 기기별로 독립적인 TTL을 가집니다. (Key: {prefix}:{userId}:{deviceId})<br>
 * 2. <b>Session Index</b>: Sorted Set(ZSet)을 사용하여 로그인 시간순으로 정렬된 기기 목록을 관리합니다. (Key: {prefix}:sessions:{userId})
 * @see TokenPersistencePort
 */
@Slf4j
@Component
public class TokenPersistenceAdapter implements TokenPersistencePort {

    private final RedisTemplate<String, Object> redisTemplate;
    private final int maxToken;

    // Key 구성을 위한 접두어/접미어
    private final String rtPrefix;
    private final String blPrefix;
    private final String sessionSuffix = "sessions"; // 세션 인덱스용 구분자

    public TokenPersistenceAdapter(
            RedisTemplate<String, Object> redisTemplate,
            RedisProperties redisProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.maxToken = redisProperties.auth().maxToken();
        this.rtPrefix = redisProperties.auth().prefix();
        this.blPrefix = redisProperties.blacklist().prefix();
    }

    // =================================================================================
    //  리프레시 토큰 관리 (멀티 디바이스 & 세션 제어)
    // =================================================================================

    /**
     * @responsibility 토큰 저장 및 세션 인덱스 업데이트 (세션 제한 로직 포함)
     */
    @Override
    public void saveRefreshToken(Long userId, String deviceId, String refreshToken, Duration expiry) {
        String tokenKey = createTokenKey(userId, deviceId);
        String sessionKey = createSessionIndexKey(userId);
        long timestamp = System.currentTimeMillis();

        // 1. 실제 토큰 데이터 저장 (각 기기별 독립 TTL 설정)
        redisTemplate.opsForValue().set(tokenKey, refreshToken, expiry);

        // 2. 세션 인덱스(ZSet)에 기기 ID와 로그인 시간(Score) 등록
        redisTemplate.opsForZSet().add(sessionKey, deviceId, timestamp);
        // 세션 인덱스 키 자체의 만료 시간은 넉넉하게 잡거나(예: 30일), 토큰 만료 시점에 맞춰 관리해야 함.
        // 여기서는 편의상 가장 긴 토큰 수명보다 길게 갱신.
        redisTemplate.expire(sessionKey, Duration.ofDays(30));

        // 3. 최대 세션 개수 초과 시 가장 오래된 세션 삭제 (LRU)
        manageSessionLimit(userId, sessionKey);
    }

    /**
     * @responsibility 특정 기기의 토큰 값 조회
     */
    @Override
    public String getRefreshToken(Long userId, String deviceId) {
        String tokenKey = createTokenKey(userId, deviceId);
        Object token = redisTemplate.opsForValue().get(tokenKey);
        return token != null ? token.toString() : null;
    }

    /**
     * @responsibility 특정 기기의 세션 로그아웃
     */
    @Override
    public void deleteRefreshToken(Long userId, String deviceId) {
        String tokenKey = createTokenKey(userId, deviceId);
        String sessionKey = createSessionIndexKey(userId);

        // 1. 토큰 데이터 삭제
        redisTemplate.delete(tokenKey);
        // 2. 세션 인덱스에서 해당 기기 제거
        redisTemplate.opsForZSet().remove(sessionKey, deviceId);
    }

    /**
     * @responsibility 전체 기기 로그아웃 (비밀번호 변경 등)
     */
    @Override
    public void deleteAllRefreshTokens(Long userId) {
        String sessionKey = createSessionIndexKey(userId);

        // 1. 인덱스에서 모든 기기 ID 조회
        Set<Object> deviceIds = redisTemplate.opsForZSet().range(sessionKey, 0, -1);

        if (deviceIds != null && !deviceIds.isEmpty()) {
            // 2. 각 기기별 토큰 Key 생성 후 일괄 삭제
            Set<String> tokenKeys = deviceIds.stream()
                    .map(deviceId -> createTokenKey(userId, deviceId.toString()))
                    .collect(Collectors.toSet());

            redisTemplate.delete(tokenKeys);
        }

        // 3. 세션 인덱스 자체 삭제
        redisTemplate.delete(sessionKey);
    }

    /**
     * @responsibility 현재 접속 중인 기기 목록 조회
     */
    @Override
    public Set<String> getAllDeviceIds(Long userId) {
        String sessionKey = createSessionIndexKey(userId);
        Set<Object> deviceIds = redisTemplate.opsForZSet().range(sessionKey, 0, -1);

        if (deviceIds == null) {
            return Collections.emptySet();
        }

        return deviceIds.stream()
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    /**
     * @responsibility 가장 오래된 세션 1개 강제 삭제
     */
    @Override
    public void removeOldestSession(Long userId) {
        String sessionKey = createSessionIndexKey(userId);

        // ZSet에서 Score가 가장 낮은(오래된) 1개 조회
        Set<Object> oldestDeviceSet = redisTemplate.opsForZSet().range(sessionKey, 0, 0);

        if (oldestDeviceSet != null && !oldestDeviceSet.isEmpty()) {
            String oldestDeviceId = oldestDeviceSet.iterator().next().toString();
            // 해당 기기 삭제 위임
            deleteRefreshToken(userId, oldestDeviceId);
            log.info("Session limit exceeded. Removed oldest device: userId={}, deviceId={}", userId, oldestDeviceId);
        }
    }

    /**
     * @responsibility 현재 활성 세션 개수 조회
     */
    @Override
    public long getSessionCount(Long userId) {
        String sessionKey = createSessionIndexKey(userId);
        Long count = redisTemplate.opsForZSet().zCard(sessionKey);
        return count != null ? count : 0;
    }

    /**
     * @responsibility 토큰 검증
     */
    @Override
    public boolean existsRefreshToken(Long userId, String deviceId, String refreshToken) {
        String storedToken = getRefreshToken(userId, deviceId);
        return storedToken != null && storedToken.equals(refreshToken);
    }


    // =================================================================================
    //  액세스 토큰 블랙리스트 (기존 로직 유지)
    // =================================================================================

    @Override
    public void blacklistAccessToken(String accessToken, Duration remainingTime) {
        redisTemplate.opsForValue().set(createBlacklistKey(accessToken), "logout", remainingTime);
    }

    @Override
    public boolean isBlacklisted(String accessToken) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(createBlacklistKey(accessToken)));
    }


    // =================================================================================
    //  Private Helpers
    // =================================================================================

    /**
     * 세션 개수 제한을 확인하고 초과 시 삭제하는 내부 로직
     */
    private void manageSessionLimit(Long userId, String sessionKey) {
        Long currentSize = redisTemplate.opsForZSet().zCard(sessionKey);
        if (currentSize != null && currentSize > maxToken) {
            // maxToken을 초과한 만큼 반복해서 삭제 (혹은 removeOldestSession 호출)
            long removeCount = currentSize - maxToken;
            for (int i = 0; i < removeCount; i++) {
                removeOldestSession(userId);
            }
        }
    }

    // Key: {prefix}:{userId}:{deviceId}  (예: RT:101:mobile-uuid-1234)
    private String createTokenKey(Long userId, String deviceId) {
        return String.format("%s:%s:%s", rtPrefix, userId, deviceId);
    }

    // Key: {prefix}:sessions:{userId} (예: RT:sessions:101)
    private String createSessionIndexKey(Long userId) {
        return String.format("%s:%s:%s", rtPrefix, sessionSuffix, userId);
    }

    // Key: {prefix}:{accessToken} (예: BL:eyJhbGc...)
    private String createBlacklistKey(String accessToken) {
        return String.format("%s:%s", blPrefix, accessToken);
    }
}