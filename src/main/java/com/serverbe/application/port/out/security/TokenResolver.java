package com.serverbe.application.port.out.security;

import com.serverbe.domain.model.user.vo.Role;
import org.springframework.security.core.Authentication;

import java.time.Instant;

/**
 * @responsibility 발행된 보안 토큰(Access/Refresh Token)의 유효성을 검증하고, 토큰 내부에 포함된 사용자 정보를 해석하여 추출하는 아웃바운드 포트 인터페이스입니다.
 * JWT와 같은 구체적인 토큰 기술 형식을 추상화하여 보안 로직의 독립성을 제공합니다.
 */
public interface TokenResolver {

    /**
     * @responsibility 액세스 토큰에 포함된 클레임(Claims) 정보를 바탕으로 Spring Security의 인증 객체({@link Authentication})를 생성합니다.
     * @param accessToken 정보를 추출할 액세스 토큰 문자열
     * @return 인증된 사용자의 신원 및 권한 정보를 담고 있는 {@link Authentication} 객체
     */
    Authentication getAuthentication(String accessToken);

    /**
     * @responsibility 액세스 토큰의 서명 위변조 여부, 구조적 결함 및 만료 시간을 확인하여 사용 가능 여부를 판별합니다.
     * @param accessToken 검증 대상이 되는 액세스 토큰
     * @return 토큰이 유효하며 사용 가능한 상태인 경우 true, 그렇지 않으면 false
     */
    boolean validateAccessToken(String accessToken);

    /**
     * @responsibility 리프레시 토큰의 유효성을 검증합니다. 액세스 토큰과 별도의 만료 정책이나 서명을 확인할 때 사용됩니다.
     * @param refreshToken 검증 대상이 되는 리프레시 토큰
     * @return 리프레시 토큰이 유효한 경우 true, 만료되었거나 변조된 경우 false
     */
    boolean validateRefreshToken(String refreshToken);

    /**
     * @responsibility 토큰의 페이로드(Payload)에서 사용자의 시스템 고유 식별자(ID)를 추출합니다.
     * @param accessToken 식별자를 추출할 액세스 토큰
     * @return 추출된 사용자의 고유 식별자 (Long 타입 ID)
     */
    Long getIdFromToken(String accessToken);

    /**
     * @responsibility 토큰에 기록된 사용자의 시스템 권한 정보를 추출하여 도메인 모델 형식으로 반환합니다.
     * @param accessToken 권한 정보를 추출할 액세스 토큰
     * @return 도메인 레이어에서 정의된 사용자 권한 객체 {@link Role}
     */
    Role getRoleFromToken(String accessToken);

    /**
     * @responsibility 토큰이 만료되는 정확한 시점 정보를 추출합니다.
     * @param accessToken 만료 시간을 조회할 액세스 토큰
     * @return 토큰의 만료 시점을 나타내는 {@link Instant} 객체
     */
    Instant getExpirationFromToken(String accessToken);
}