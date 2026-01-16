package com.serverbe.adapter.out.persistence.token;

import com.serverbe.application.port.out.token.TokenPersistencePort;
import com.serverbe.domain.exception.auth.AuthErrorCode;
import com.serverbe.domain.exception.auth.AuthException;
import com.serverbe.infrastructure.config.properties.JwtProperties;
import com.serverbe.infrastructure.config.properties.RedisProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Duskafka
 * @responsibility Redis를 활용하여 멀티 디바이스 환경의 토큰 영속성을 관리합니다.
 * @implSpec 1. <b>Token Data</b>: Key-Value(String) 구조로 저장하며, 각 기기별로 독립적인 TTL을 가집니다. (Key: {prefix}:{userId}:{deviceId})<br>
 * 2. <b>Session Index</b>: Sorted Set(ZSet)을 사용하여 로그인 시간순으로 정렬된 기기 목록을 관리합니다. (Key: {prefix}:sessions:{userId})
 * @see TokenPersistencePort
 */
@Slf4j
@Component
public class TokenPersistenceAdapter implements TokenPersistencePort {

    private final RedisTemplate<String, Object> redisTemplate;
    private final int maxToken;
    private final Duration refreshTokenExpireDays;

    // Key 구성을 위한 접두어/접미어
    private final String authPrefix;
    private final String authSuffix;
    private final String atBlacklistPrefix;
    private final String rtBlacklistPrefix;
    private final String sessionSuffix;

    public TokenPersistenceAdapter(
            RedisTemplate<String, Object> redisTemplate,
            RedisProperties redisProperties,
            JwtProperties jwtProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.maxToken = redisProperties.auth().maxToken();
        this.refreshTokenExpireDays = jwtProperties.refreshToken().expirationDays();
        this.authPrefix = redisProperties.auth().prefix();
        this.authSuffix = redisProperties.auth().suffix();
        this.sessionSuffix = redisProperties.session().suffix();
        this.atBlacklistPrefix = redisProperties.blacklist().accessTokenPrefix();
        this.rtBlacklistPrefix = redisProperties.blacklist().refreshTokenPrefix();
    }


    /**
     * @responsibility 토큰 저장 및 세션 인덱스 업데이트 (세션 제한 로직 포함)
     * @implSpec 원자성을 보장하도록 execute로 묶었습니다.
     */
    @Override
    public void saveRefreshToken(Long userId, String deviceId, String refreshToken, Duration expiry) {
        String tokenKey = createTokenKey(userId, deviceId);
        String sessionKey = createSessionIndexKey(userId);
        long timestamp = System.currentTimeMillis();

        // 트랜잭션으로 묶어 원자성 보장
        redisTemplate.execute(new SessionCallback<List<Object>>() {
            @Override
            public List<Object> execute(RedisOperations operations) {
                operations.multi();
                operations.opsForValue().set(tokenKey, refreshToken, expiry);
                operations.opsForZSet().add(sessionKey, deviceId, timestamp);
                operations.expire(sessionKey, refreshTokenExpireDays);
                return operations.exec();
            }
        });

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
     * @param userId 사용자 식별자
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
     * @param userId 사용자 식별자
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
     * @param userId 사용자 식별자
     * @responsibility 현재 활성 세션 개수 조회
     */
    @Override
    public long getSessionCount(Long userId) {
        String sessionKey = createSessionIndexKey(userId);
        Long count = redisTemplate.opsForZSet().zCard(sessionKey);
        return count != null ? count : 0;
    }

    /**
     * @param userId       사용자 식별자
     * @param refreshToken 존재하는지 확인할 리프레시 토큰
     * @param deviceId     기기 식별자
     * @responsibility 토큰 검증
     */
    @Override
    public boolean existsRefreshToken(Long userId, String deviceId, String refreshToken) {
        String storedToken = getRefreshToken(userId, deviceId);
        return storedToken != null && storedToken.equals(refreshToken);
    }

    /**
     * 액세스 토큰을 블랙리스트에 등록합니다.
     *
     * @param accessToken   블랙리스트에 등록할 액세스 토큰
     * @param remainingTime 액세스 토큰의 남은 시간
     */
    @Override
    public void blacklistAccessToken(String accessToken, Duration remainingTime) {
        redisTemplate.opsForValue().set(createAccessTokenBlacklistKey(accessToken), "logout", remainingTime);
    }

    /**
     * 리프레시 토큰을 블랙리스트에 등록합니다.
     *
     * @param refreshToken  블랙리스트에 등록할 리프레시 토큰
     * @param remainingTime 리프레시 토큰의 남은 시간
     */
    @Override
    public void blacklistRefreshToken(String refreshToken, Duration remainingTime) {
        redisTemplate.opsForValue().set(createRefreshTokenBlacklistKey(refreshToken), "used", remainingTime);
    }

    /**
     * 액세스 토큰이 블랙리스트에 등록되었는지 확인합니다.
     *
     * @param accessToken 블랙리스트에 등록되었는지 확인할 액세스 토큰
     * @return 등록되었다면 true, 아니면 false
     */
    @Override
    public boolean isAccessTokenBlacklisted(String accessToken) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(createAccessTokenBlacklistKey(accessToken)));
    }

    /**
     * 리프레시 토큰이 블랙리스트에 등록되어있는지 확인합니다.
     *
     * @param refreshToken 블랙리스트에 등록되었는지 확인할 리프레시 토큰
     * @return 등록되었다면 true, 아니면 false
     */
    @Override
    public boolean isRefreshTokenBlacklisted(String refreshToken) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(createRefreshTokenBlacklistKey(refreshToken)));
    }

    /**
     * 리프레시 토큰 순환 메소드
     *
     * @param userId          사용자 식별자
     * @param deviceId        기기 식별자
     * @param oldRefreshToken 오래된(이전의) 리프레시 토큰
     * @param newRefreshToken 새로 발급한 리프레시 토큰
     * @param expiry          기존에 등록된 리프레시 토큰이 사용되고 등록할 블랙리스트의 기한
     * @implSpec 원자성을 지키기 위해 execute로 묶었습니다.
     */
    @Override
    public void rotateRefreshToken(Long userId, String deviceId, String oldRefreshToken, String newRefreshToken, Duration expiry) {
        String tokenKey = createTokenKey(userId, deviceId);
        String sessionKey = createSessionIndexKey(userId);
        String blacklistKey = createRefreshTokenBlacklistKey(oldRefreshToken); // 기존 토큰 해싱 키
        long timestamp = System.currentTimeMillis();

        redisTemplate.execute(new SessionCallback<List<Object>>() {
            @Override
            public List<Object> execute(RedisOperations operations) {
                operations.multi(); // 트랜잭션 시작

                // 1. 신규 토큰 저장 (덮어쓰기)
                operations.opsForValue().set(tokenKey, newRefreshToken, expiry);

                // 2. 기존 토큰 블랙리스트 등록 (최대 수명 적용)
                operations.opsForValue().set(blacklistKey, "used", expiry);

                // 3. 세션 인덱스 시간 갱신 및 만료시간 연장
                operations.opsForZSet().add(sessionKey, deviceId, timestamp);
                operations.expire(sessionKey, refreshTokenExpireDays);

                return operations.exec(); // 한 번에 실행
            }
        });

        // 4. 세션 개수 제한 로직 (기존 메서드 재사용)
        manageSessionLimit(userId, sessionKey);
    }


    /**
     * 세션 개수 제한을 확인하고 초과 시 삭제하는 내부 로직
     *
     * @param userId     사용자 식별자
     * @param sessionKey Redis에 조회할 세션 키
     */
    private void manageSessionLimit(Long userId, String sessionKey) {
        Long currentSize = redisTemplate.opsForZSet().zCard(sessionKey);
        if (currentSize != null && currentSize > maxToken) {
            long removeCount = currentSize - maxToken;

            // 1. 삭제할 기기 ID들을 한꺼번에 가져옴 (0번부터 removeCount-1번까지가 가장 오래된 것들)
            Set<Object> oldestDeviceIds = redisTemplate.opsForZSet().range(sessionKey, 0, removeCount - 1);

            if (oldestDeviceIds != null && !oldestDeviceIds.isEmpty()) {
                // 2. 해당 기기들의 토큰 데이터 일괄 삭제
                List<String> tokenKeysToDelete = oldestDeviceIds.stream()
                        .map(deviceId -> createTokenKey(userId, deviceId.toString()))
                        .collect(Collectors.toList());
                redisTemplate.delete(tokenKeysToDelete);

                // 3. 세션 인덱스에서 한꺼번에 제거
                redisTemplate.opsForZSet().removeRange(sessionKey, 0, removeCount - 1);

                log.info("[SESSION] 사용자({})세션 한도 초과, 오래된 세션 {}개를 삭제합니다..", userId, removeCount);
            }
        }
    }

    /**
     * 리프레시 토큰을 등록할 때 키를 생성합니다.
     *
     * @param userId   사용자 식별자
     * @param deviceId 기기 식별자
     */
    private String createTokenKey(Long userId, String deviceId) {
        return String.format("%s:%d:%s:%s", authPrefix, userId, authSuffix, deviceId);
    }

    /**
     * @param userId 사용자 식별자
     * @implNote Key: {prefix}:sessions:{userId} (예: RT:sessions:101)
     */
    private String createSessionIndexKey(Long userId) {
        return String.format("%s:%s:%s", authPrefix, sessionSuffix, userId);
    }

    /**
     * AT 블랙리스트 키
     *
     * @implNote BL:AT:{token}
     */
    private String createAccessTokenBlacklistKey(String accessToken) {
        return String.format("%s:%s", atBlacklistPrefix, accessToken);
    }

    /**
     * RT 블랙리스트 키
     *
     * @implNote BL:RT:{token} (기기 정보 없음)
     * @implSpec 해실 실패 시 롤백합니다.
     */
    private String createRefreshTokenBlacklistKey(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(refreshToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return String.format("%s:%s", rtBlacklistPrefix, hexString);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new AuthException(AuthErrorCode.FAILED_HASH_REFRESH_TOKEN, e.getMessage());
        }
    }
}