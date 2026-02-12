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

        redisTemplate.execute(new SessionCallback<List<Object>>() {
            @Override
            public List<Object> execute(RedisOperations operations) {
                // 1. [WATCH] 키 감시 (낙관적 락)
                // 삭제 프로세스 도중 새로운 로그인이 발생하면 트랜잭션을 취소하기 위함
                operations.watch(sessionKey);

                // 2. [READ] 삭제 대상 조회 (Transaction 외부)
                Set<Object> deviceIds = operations.opsForZSet().range(sessionKey, 0, -1);

                // 3. [MULTI] 트랜잭션 시작
                operations.multi();

                // 4. [WRITE] 삭제 명령 큐잉
                if (deviceIds != null && !deviceIds.isEmpty()) {
                    Set<String> tokenKeys = deviceIds.stream()
                            .map(deviceId -> createTokenKey(userId, deviceId.toString()))
                            .collect(Collectors.toSet());
                    // 개별 토큰 데이터 삭제
                    operations.delete(tokenKeys);
                }

                // 세션 인덱스(목록) 삭제
                operations.delete(sessionKey);

                // 5. [EXEC] 원자적 실행
                return operations.exec();
            }
        });

        log.info("[SESSION] 전역 로그아웃이 성공적으로 처리되었습니다 User={}", userId);
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
        long timestamp = System.currentTimeMillis();

        // 트랜잭션 시작 전, 기존 토큰의 남은 수명(TTL) 조회
        // getExpire는 트랜잭션(multi) 내부에서 호출하면 null을 반환하므로 외부에서 조회해야 합니다.
        Long remainingTimeMillis = redisTemplate.getExpire(tokenKey, TimeUnit.MILLISECONDS);

        // 남은 시간이 없거나(-2: 키 없음), 만료됨(-1: 무제한이나 여기선 해당없음)인 경우 방어 로직
        // 키가 없다는 건 이미 만료되었거나 삭제된 상태이므로, 블랙리스트에는 최소한의 시간(예: 5분)만 잡아두거나,
        // 보안 정책에 따라 원래 설정된 refreshTokenExpireDays를 사용할 수도 있습니다.
        // 여기서는 '메모리 절약'이 목적이므로 남은 시간이 있다면 그것을, 없다면 5분의 버퍼를 둡니다.
        Duration blacklistTtl = (remainingTimeMillis != null && remainingTimeMillis > 0)
                ? Duration.ofMillis(remainingTimeMillis)
                : Duration.ofMinutes(5);

        redisTemplate.execute(new SessionCallback<List<Object>>() {
            @Override
            public List<Object> execute(RedisOperations operations) {
                operations.multi(); // 트랜잭션 시작

                // 1. 신규 토큰 저장 (덮어쓰기) - 새 토큰의 수명 적용
                operations.opsForValue().set(tokenKey, newRefreshToken, newTokensExpiry);

                // 2. 기존 토큰 블랙리스트 등록 - [변경 포인트 2] 조회한 잔여 수명(blacklistTtl) 적용
                operations.opsForValue().set(blacklistKey, "used", blacklistTtl);

                // 3. 세션 인덱스 시간 갱신 및 만료시간 연장
                operations.opsForZSet().add(sessionKey, deviceId, timestamp);
                operations.expire(sessionKey, refreshTokenExpireDays);

                return operations.exec(); // 한 번에 실행
            }
        });

        // 4. 세션 개수 제한 로직
        manageSessionLimit(userId, sessionKey);

        log.info("[RTR] 토큰 교체 완료. Old Token TTL(Blacklist): {}ms", blacklistTtl.toMillis());
    }


    /**
     * @param userId     세션을 정리할 사용자 식별자
     * @param sessionKey Redis에 저장된 해당 사용자의 세션 인덱스 Key (ZSet)
     * @responsibility 사용자의 최대 동시 접속 세션 수(Max Session Limit)를 초과하지 않도록 관리하며, 초과 시 가장 오래된 세션을 정리합니다.
     * @implSpec 1. <b>Optimistic Locking (낙관적 락)</b>: Redis의 {@code WATCH} 명령어를 사용하여 조회(Read)와 수정(Write) 사이의 원자성을 보장합니다.<br>
     * 2. <b>Safe Failure</b>: 만약 로직 수행 도중 다른 스레드/프로세스에 의해 {@code sessionKey}가 변경되었다면(예: 새로운 로그인),
     * {@code exec()} 실행 시 트랜잭션은 자동으로 취소(Rollback)되며 데이터 정합성을 유지합니다.
     * @implNote <b>Transaction Flow</b>:<br>
     * Redis 트랜잭션 내에서 조건문 분기가 불가능하므로, {@code SessionCallback}을 통해 다음 순서를 엄격히 따릅니다.<br>
     * 1. {@code WATCH}: 키 감시 시작<br>
     * 2. {@code READ}: 현재 개수 조회 및 삭제 대상 계산 (Java Level)<br>
     * 3. {@code MULTI}: 트랜잭션 시작 (이후 명령어는 큐에 적재)<br>
     * 4. {@code WRITE}: 삭제 명령어(Delete, ZRem) 예약<br>
     * 5. {@code EXEC}: 원자적 실행
     */
    private void manageSessionLimit(Long userId, String sessionKey) {
        redisTemplate.execute(new SessionCallback<List<Object>>() {
            @Override
            public List<Object> execute(RedisOperations operations) {
                // 1. [WATCH] 키를 감시 시작 (낙관적 락)
                // 이후 exec()가 호출될 때까지 이 키에 변경사항이 생기면 트랜잭션은 취소됨
                operations.watch(sessionKey);

                // 2. [READ] 현재 상태 조회 (트랜잭션 시작 전)
                Long currentSize = operations.opsForZSet().zCard(sessionKey);

                // 한도가 초과되지 않았다면 감시 해제 후 종료
                if (currentSize == null || currentSize <= maxToken) {
                    operations.unwatch();
                    return Collections.emptyList();
                }

                // 3. [READ] 삭제할 대상 계산
                long removeCount = currentSize - maxToken;
                Set<Object> oldestDeviceIds = operations.opsForZSet().range(sessionKey, 0, removeCount - 1);

                // 4. [MULTI] 트랜잭션 시작 (이제부터 명령어는 큐에 쌓임)
                operations.multi();

                if (oldestDeviceIds != null && !oldestDeviceIds.isEmpty()) {
                    // (1) 실제 토큰 데이터 삭제 명령 큐잉
                    List<String> tokenKeysToDelete = oldestDeviceIds.stream()
                            .map(deviceId -> createTokenKey(userId, deviceId.toString()))
                            .collect(Collectors.toList());
                    operations.delete(tokenKeysToDelete);

                    // (2) 세션 인덱스(ZSet) 제거 명령 큐잉
                    operations.opsForZSet().removeRange(sessionKey, 0, removeCount - 1);
                }

                // 5. [EXEC] 일괄 실행
                // 만약 watch 이후 다른 스레드가 sessionKey를 건드렸다면 여기서 빈 리스트가 반환되고 아무 일도 안 일어남 (안전)
                return operations.exec();
            }
        });
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
     * @implNote Key: {prefix}:sessions:{userId} (예: user:sessions:101)
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