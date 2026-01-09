package com.serverbe.application.service;

import com.serverbe.application.port.in.oauth.LogoutUseCase;
import com.serverbe.application.port.out.security.TokenResolver;
import com.serverbe.application.port.out.token.TokenPersistencePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * @author Duskafka
 * @responsibility 사용자의 로그아웃 요청을 처리하여 현재 세션 또는 모든 기기의 세션을 무효화합니다.
 * @implSpec {@link LogoutUseCase} 인터페이스의 구현체로, 토큰 정보 추출과 영속성 계층의 상태 변경을 조율합니다.
 */
@Service
@RequiredArgsConstructor
public class LogoutService implements LogoutUseCase {

    private final TokenPersistencePort tokenPersistencePort;
    private final TokenResolver tokenResolver;

    /**
     * @requirement UC-AUTH-02: 현재 기기 로그아웃
     * @responsibility 요청이 들어온 특정 기기의 세션만 종료하고 해당 액세스 토큰을 차단합니다.
     * @implSpec
     * 1. {@link TokenResolver#getIdFromToken(String)}을 사용하여 토큰 소유자를 식별합니다.<br>
     * 2. {@link TokenPersistencePort#removeSpecificRefreshToken(Long, String)}을 통해 요청된 특정 리프레시 토큰만 삭제합니다.<br>
     * 3. 현재 시각과 만료 시각의 차이를 계산하여 액세스 토큰을 블랙리스트에 등록합니다.
     * @implNote 액세스 토큰이 블랙리스트에 등록되면 만료 전이라도 시스템 접근이 즉시 차단됩니다.
     * @param accessToken  사용자가 로그아웃에 사용한 액세스 토큰
     * @param refreshToken 사용자가 로그아웃을 요청한 특정 리프레시 토큰
     */
    @Override
    public void logout(String accessToken, String refreshToken) {
        // 1. 토큰에서 사용자 ID 추출
        Long userId = tokenResolver.getIdFromToken(accessToken);

        // 2. Redis에서 리프레시 토큰 삭제 (더 이상 재발급 불가)
        tokenPersistencePort.removeSpecificRefreshToken(userId, refreshToken);

        // 3. 액세스 토큰의 남은 유효 시간 계산
        Instant expiration = tokenResolver.getExpirationFromToken(accessToken);
        Duration remainingTime = Duration.between(Instant.now(), expiration);

        // 4. 액세스 토큰 블랙리스트 등록 (남은 시간 동안만)
        if (!remainingTime.isNegative()) {
            tokenPersistencePort.blacklistAccessToken(accessToken, remainingTime);
        }
    }

    /**
     * @requirement UC-AUTH-03: 모든 기기 로그아웃
     * @responsibility 해당 사용자의 모든 활성화된 세션을 일괄 종료하여 보안 사고에 대응하거나 전체 로그아웃을 수행합니다.
     * @implSpec
     * 1. {@link TokenPersistencePort#deleteRefreshToken(Long)}을 호출하여 유저와 연결된 모든 리프레시 토큰을 제거합니다.<br>
     * 2. 현재 사용 중인 액세스 토큰 역시 블랙리스트에 등록하여 즉시 무효화합니다.
     * @implNote 이 기능은 계정 탈취 의심 상황이나 비밀번호 변경 후 강제 로그아웃 시나리오에서 핵심적인 역할을 합니다.
     * @param accessToken 사용자가 로그아웃에 사용한 액세스 토큰
     */
    @Override
    public void globalLogout(String accessToken) {
        // 1. 토큰에서 사용자 ID 추출
        Long userId = tokenResolver.getIdFromToken(accessToken);

        // 2. 모든 리프레쉬 토큰 삭제
        tokenPersistencePort.deleteRefreshToken(userId);

        // 3. 액세스 토큰의 남은 유효 시간 계산
        Instant expiration = tokenResolver.getExpirationFromToken(accessToken);
        Duration remainingTime = Duration.between(Instant.now(), expiration);

        // 4. 액세스 토큰 블랙리스트 등록 (남은 시간 동안만)
        if (!remainingTime.isNegative()) {
            tokenPersistencePort.blacklistAccessToken(accessToken, remainingTime);
        }
    }
}