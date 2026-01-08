package com.serverbe.application.port.out.security;

import com.serverbe.domain.model.user.vo.Role;
import com.serverbe.infrastructure.error.BusinessException;
import org.springframework.security.core.Authentication;

import java.time.Instant;

public interface TokenResolver {
    /**
     * @param accessToken 액세스 토큰
     * @return 추출된 {@link Authentication} 객체
     * @responsibility JWT 토큰에서 {@link Authentication} 객체를 추출하는 책임.
     */
    Authentication getAuthentication(String accessToken);

    /**
     * @param accessToken 검증할 액세스 토큰
     * @return 액세스 토큰 값이 유효하다면 true, 아니라면 false
     * @responsibility 토큰의 서명 및 구조적 유효성을 검증하는 책임.
     */
    boolean validateAccessToken(String accessToken);

    /**
     * @param refreshToken 검증할 리프레시 토큰
     * @return 리프레시 토큰이 유효하면 true, 아니라면 false
     * @responsibility 리프레시 토큰을 검증하는 메소드.
     */
    boolean validateRefreshToken(String refreshToken);

    /**
     * @param accessToken 고유 식별자를 추출할 액세스 토큰
     * @return 추출한 고유 식별자(ID)
     * @responsibility 액세스 토큰의 Claim 부분에서 사용자 고유 식별자를 가져오는 역할 책임.
     */
    Long getIdFromToken(String accessToken);

    /**
     * @param accessToken 추출에 사용할 액세스 토큰
     * @return 추출한 Role 객체(Enum)
     * @responsibility 토큰에서 {@link Role}을 추출하는 책임.
     */
    Role getRoleFromToken(String accessToken);

    /**
     * @param accessToken 액세스 토큰
     * @return 만료 시간 (Instant)
     * @responsibility 액세스 토큰에서 {@link Instant}을 추출하는 책임.
     */
    Instant getExpirationFromToken(String accessToken);
}
