package com.serverbe.domain.model.user;

import com.serverbe.application.port.out.dto.oauth.OAuthUserInfoResult;
import com.serverbe.domain.model.user.vo.OAuthProvider;
import com.serverbe.domain.model.user.vo.Role;

import java.util.List;

/**
 * @responsibility 시스템의 핵심 <b>유저 엔티티</b>를 나타내는 불변 객체입니다.
 * @param id 시스템 내부 식별자 (PK)
 * @param oauthId 소셜 서비스에서 제공하는 고유 식별자
 * @param provider 소셜 로그인 제공자 {@link OAuthProvider}
 * @param email 사용자 이메일
 * @param nickname 사용자 닉네임
 * @param role 사용자 권한 {@link Role}
 * @param statusMessage 사용자의 상태 메시지
 * @param oauthRefreshToken 외부 소셜 서비스의 리프레시 토큰
 */
public record User(
        Long id,
        String oauthId,
        OAuthProvider provider,
        String email,
        String nickname,
        Role role,
        String statusMessage,
        String oauthRefreshToken
) {
    /**
     * @responsibility 소셜 서비스의 리프레시 토큰을 갱신한 새로운 유저 객체를 반환합니다.
     * @param newToken 새로 발급받은 소셜 리프레시 토큰
     * @return 토큰이 갱신된 {@link User} 인스턴스
     */
    public User renewOauthRefreshToken(String newToken) {
        return new User(
                this.id,
                this.oauthId,
                this.provider,
                this.email,
                this.nickname,
                this.role,
                this.statusMessage,
                newToken
        );
    }

    /**
     * @responsibility Spring Security 등 인증 프레임워크와의 호환을 위해 권한 리스트를 반환합니다.
     * @return 사용자의 권한 명칭 리스트
     */
    public List<String> getRoleList() {
        return List.of(role.name());
    }

    /**
     * @responsibility <b>신규 가입</b>을 위한 유저 객체를 생성하는 정적 팩토리 메서드입니다.
     * @param oauthInfo OAuth 서버로부터 획득한 사용자 정보 {@link OAuthUserInfoResult}
     * @param provider 소셜 로그인 제공자
     * @return 기본 권한(USER)이 부여된 신규 {@link User} 객체
     */
    public static User createNew(OAuthUserInfoResult oauthInfo, OAuthProvider provider) {
        return new User(
                null,
                oauthInfo.oauthId(),
                provider,
                oauthInfo.email(),
                oauthInfo.nickname(),
                Role.USER,
                null,
                oauthInfo.oauthRefreshToken()
        );
    }

    /**
     * @responsibility 외부 소셜 서비스의 최신 정보를 반영하여 유저 객체를 업데이트합니다.
     * @param oauthInfo 갱신된 소셜 사용자 정보 {@link OAuthUserInfoResult}
     * @return 정보가 동기화된 새로운 {@link User} 객체
     */
    public User updateFromOAuth(OAuthUserInfoResult oauthInfo) {
        return new User(
                this.id,
                this.oauthId,
                this.provider,
                oauthInfo.email(),
                oauthInfo.nickname(),
                this.role,
                this.statusMessage,
                oauthInfo.oauthRefreshToken()
        );
    }

    /**
     * @responsibility 사용자의 프로필 정보를 수정합니다.
     * @param nickname 변경할 닉네임
     * @param statusMessage 변경할 상태 메시지
     * @return 프로필 정보가 수정된 새로운 {@link User} 객체
     */
    public User updateProfile(String nickname, String statusMessage) {
        return new User(
                this.id,
                this.oauthId,
                this.provider,
                this.email,
                nickname,
                this.role,
                statusMessage,
                this.oauthRefreshToken
        );
    }
}