package com.serverbe.application.port.out.security;

import com.serverbe.application.port.out.dto.oauth.AccessTokenResult;
import com.serverbe.application.port.out.dto.oauth.RefreshTokenResult;
import com.serverbe.domain.model.user.vo.Role;
import com.serverbe.infrastructure.security.JwtTokenProvider;
import org.springframework.security.core.Authentication;

public interface TokenProvider {
    /**
     * 주어진 {@link Authentication} 객체를 사용하여 액세스 토큰을 생성합니다.
     *
     * @param id   사용자의 고유 식별자
     * @param role 사용자의 권한.
     * @return 생성된 액세스 토큰 정보를 담은 {@link AccessTokenResult} DTO
     * @responsibility {@link Authentication} 객체를 사용하여 액세스 토큰을 생성한다.
     */
    AccessTokenResult generateAccessToken(Long id, Role role);

    /**
     * @param id   사용자의 고유 식별자
     * @param role 사용자의 권한
     * @return 생성된 리프레시 토큰 문자열과 관련 정보를 담은 {@link RefreshTokenResult} DTO
     */
    RefreshTokenResult generateRefreshToken(Long id, Role role);
}
