package com.serverbe.adapter.out.persistence.token;

import com.serverbe.application.port.out.token.TokenPersistencePort;
import com.serverbe.domain.exception.auth.AuthErrorCode;
import com.serverbe.domain.exception.auth.AuthException;
import com.serverbe.infrastructure.config.properties.JwtProperties;
import com.serverbe.infrastructure.config.properties.RedisProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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

    // Lua Scripts 주입
    private final RedisScript<Boolean> saveTokenScript;
    private final RedisScript<Boolean> rotateTokenScript;
    private final RedisScript<Boolean> globalLogoutScript;
    private final RedisScript<Boolean> deleteTokenScript;

    // Key 구성을 위한 접두어/접미어
    private final String authPrefix;
    private final String authSuffix;
    private final String atBlacklistPrefix;
    private final String rtBlacklistPrefix;
    private final String sessionSuffix;

    public TokenPersistenceAdapter(
            RedisTemplate<String, Object> redisTemplate,
            RedisProperties redisProperties,
            JwtProperties jwtProperties,

            @Qualifier("saveTokenScript") RedisScript<Boolean> saveTokenScript,
            @Qualifier("rotateTokenScript") RedisScript<Boolean> rotateTokenScript,
            @Qualifier("globalLogoutScript") RedisScript<Boolean> globalLogoutScript,
            @Qualifier("deleteTokenScript") RedisScript<Boolean> deleteTokenScript
    ) {
        this.redisTemplate = redisTemplate;
        this.saveTokenScript = saveTokenScript;
        this.rotateTokenScript = rotateTokenScript;
        this.globalLogoutScript = globalLogoutScript;
        this.deleteTokenScript = deleteTokenScript;

        // Properties 설정 (기존과 동일)
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
        // 스크립트 내부에서 키를 재조립하기 위한 접두사 (예: "auth:100:rt:")
        String tokenKeyPrefix = getTokenKeyPrefix(userId);

        redisTemplate.execute(saveTokenScript,
                List.of(sessionKey, tokenKey),             // KEYS
                deviceId,                                  // ARGV[1]
                refreshToken,                              // ARGV[2]
                String.valueOf(expiry.toMillis()),         // ARGV[3]
                String.valueOf(System.currentTimeMillis()),// ARGV[4]
                String.valueOf(refreshTokenExpireDays.toMillis()), // ARGV[5]
                String.valueOf(maxToken),                  // ARGV[6]
                tokenKeyPrefix                             // ARGV[7]
        );
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

        redisTemplate.execute(deleteTokenScript,
                List.of(sessionKey, tokenKey), // KEYS
                deviceId                       // ARGV[1]
        );

         log.debug("[SESSION] 개별 로그아웃 완료. userId={}, deviceId={}", userId, deviceId);
    }


    /**
     * @param userId 세션을 모두 종료할 유저 고유 식별자
     * @responsibility 해당 사용자의 모든 인증 세션 데이터(개별 토큰 및 세션 인덱스)를 완전히 제거하여 전역 로그아웃(Global Logout)을 수행합니다.
     * @implSpec <b>Atomicity (원자성)</b>: Redis 트랜잭션({@code WATCH/MULTI/EXEC})을 사용하여 다음을 보장합니다.<br>
     * 1. <b>All-or-Nothing</b>: 개별 토큰 삭제와 세션 인덱스 삭제가 하나의 단위로 실행됩니다. 중간에 실패 시 롤백됩니다.<br>
     * 2. <b>Consistency</b>: 삭제 작업을 수행하는 동안 새로운 로그인이 발생하여 인덱스가 변경될 경우, 트랜잭션을 취소하여 데이터 정합성을 지킵니다.
     * @implNote <b>Implementation Details</b>:<br>
     * 단순히 {@code delete}만 호출하는 것이 아니라, {@code WATCH}를 통해 세션 인덱스 키를 감시합니다.<br>
     * 이는 삭제할 토큰 목록을 조회한 직후, 다른 요청(로그인 등)이 인덱스를 변경하지 않았음을 확인한 뒤 안전하게 삭제하기 위함입니다.
     */
    @Override
    public void deleteAllRefreshTokens(Long userId) {
        String sessionKey = createSessionIndexKey(userId);
        String tokenKeyPrefix = getTokenKeyPrefix(userId);

        redisTemplate.execute(globalLogoutScript,
                List.of(sessionKey), // KEYS
                tokenKeyPrefix       // ARGV
        );

        log.info("[SESSION] 전역 로그아웃 완료. User={}", userId);
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
            log.info("[SESSION] 세션이 한도를 초과하였습니다. 가장 오래된 세션을 삭제합니다.: userId={}, deviceId={}", userId, oldestDeviceId);
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
     * @param newTokensExpiry 새로 발급될 토큰의 유효 기간 (기존 expiry 파라미터 명확화)
     * @implSpec 1. 기존 토큰의 남은 TTL을 조회하여 블랙리스트 저장 시간으로 사용합니다.
     * 2. 원자성을 지키기 위해 execute로 묶어서 처리합니다.
     */
    @Override
    public void rotateRefreshToken(Long userId, String deviceId, String oldRefreshToken, String newRefreshToken, Duration newTokensExpiry) {
        String tokenKey = createTokenKey(userId, deviceId);
        String sessionKey = createSessionIndexKey(userId);
        String blacklistKey = createRefreshTokenBlacklistKey(oldRefreshToken);
        String tokenKeyPrefix = getTokenKeyPrefix(userId);

        redisTemplate.execute(rotateTokenScript,
                List.of(sessionKey, tokenKey, blacklistKey), // KEYS
                deviceId,                                    // ARGV[1]
                newRefreshToken,                             // ARGV[2]
                String.valueOf(newTokensExpiry.toMillis()),  // ARGV[3]
                String.valueOf(System.currentTimeMillis()),  // ARGV[4]
                String.valueOf(refreshTokenExpireDays.toMillis()), // ARGV[5]
                String.valueOf(maxToken),                    // ARGV[6]
                tokenKeyPrefix,                              // ARGV[7]
                String.valueOf(Duration.ofMinutes(5).toMillis()) // ARGV[8] (기본 블랙리스트 TTL)
        );

        log.info("[RTR] 토큰 교체 완료 (Lua Script). userId={}, deviceId={}", userId, deviceId);
    }

    @Override
    public long getSessionTtl(Long userId, String deviceId) {
        // 1. 특정 기기의 토큰 키를 생성합니다.
        String tokenKey = createTokenKey(userId, deviceId);

        // 2. Redis에서 남은 시간(Milliseconds) 조회
        Long expire = redisTemplate.getExpire(tokenKey, TimeUnit.MILLISECONDS);

        // 3. 만료되었거나 키가 없으면 0 반환, 아니면 남은 시간 반환
        return (expire != null && expire > 0) ? expire : 0;
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
     * @implNote Key: user:sessions:{userId} (예: user:sessions:101)
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

    /**
     * Lua Script 내부에서 deviceId와 결합하여 전체 토큰 키를 만들기 위한 접두사를 반환합니다.
     * createTokenKey 로직에서 deviceId만 제외한 부분
     * 결과 예시: "user:{userId}:rt:" (마지막 콜론 포함)
     */
    private String getTokenKeyPrefix(Long userId) {
        return String.format("%s:%d:%s:", authPrefix, userId, authSuffix);
    }
}