package com.serverbe.application.port.out.security;

import com.serverbe.application.port.out.dto.oauth.AccessTokenResult;
import com.serverbe.application.port.out.dto.oauth.RefreshTokenResult;
import com.serverbe.domain.model.user.vo.Role;

/**
 * @responsibility 시스템의 인증 및 인가에 필요한 보안 토큰(Access Token, Refresh Token)을 생성하기 위한 아웃바운드 포트 인터페이스입니다.
 * 사용자의 신원 정보와 권한 정보를 바탕으로 표준화된 토큰 세트를 발행하는 역할을 정의합니다.
 */
public interface TokenProvider {

    /**
     * @responsibility 사용자의 식별 정보와 권한 정보를 기반으로 짧은 유효 기간을 가진 액세스 토큰(Access Token)을 생성합니다.
     * @param id 토큰에 포함될 사용자의 고유 식별자
     * @param role 사용자에게 부여된 시스템 권한 {@link Role}
     * @return 생성된 액세스 토큰 문자열과 만료 정보를 담은 {@link AccessTokenResult}
     */
    AccessTokenResult generateAccessToken(Long id, Role role);

    /**
     * @responsibility 사용자의 세션을 유지하고 액세스 토큰을 갱신하기 위한 용도로 사용되는 긴 유효 기간의 리프레시 토큰(Refresh Token)을 생성합니다.
     * @param id 토큰에 포함될 사용자의 고유 식별자
     * @param role 사용자에게 부여된 시스템 권한 {@link Role}
     * @return 생성된 리프레시 토큰 문자열과 만료 정보를 담은 {@link RefreshTokenResult}
     */
    RefreshTokenResult generateRefreshToken(Long id, Role role);
}