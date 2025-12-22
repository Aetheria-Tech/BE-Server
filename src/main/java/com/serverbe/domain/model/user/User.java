package com.serverbe.domain.model.user;

import com.serverbe.application.port.out.dto.oauth.OAuthUserInfo;
import com.serverbe.domain.model.user.vo.OAuthProvider;
import com.serverbe.domain.model.user.vo.Role;

import java.util.List;

public record User(
        Long id,
        String oauthId,
        OAuthProvider provider,
        String email,
        String nickname,
        Role role,
        String statusMessage,
        String oauthRefreshToken // 추가
) {
    public User renewOauthRefreshToken(String newToken) {
        return new User(
                this.id,
                this.oauthId,
                this.provider,
                this.email,
                this.nickname,
                this.role,
                this.statusMessage,
                newToken // 새로운 소셜 리프레시 토큰으로 교체
        );
    }

    public List<String> getRoleList() {
        return List.of(role.name());
    }

    /**
     * [신규 가입] OAuth 정보를 바탕으로 새로운 유저 객체를 생성하는 정적 팩토리 메서드
     * 신규 가입이므로 ID는 null이며, 기본 권한은 USER로 설정합니다.
     */
    public static User createNew(OAuthUserInfo oauthInfo, OAuthProvider provider) {
        return new User(
                null, // 아직 DB 저장 전이므로 ID는 null
                oauthInfo.oauthId(),
                provider,
                oauthInfo.email(),
                oauthInfo.nickname(),
                Role.USER, // 기본 권한 설정
                null,
                oauthInfo.oauthRefreshToken()
        );
    }

    /**
     * [정보 업데이트] 기존 유저가 소셜 로그인 시 최신 정보를 반영합니다.
     * Record는 불변이므로 기존 정보를 유지하면서 변경된 값들만 교체한 새 객체를 반환합니다.
     */
    public User updateFromOAuth(OAuthUserInfo oauthInfo) {
        return new User(
                this.id,        // 고정 (PK)
                this.oauthId,   // 고정 (소셜 식별자)
                this.provider,  // 고정 (플랫폼)
                oauthInfo.email(),           // 갱신 (이메일은 바뀔 수 있음)
                oauthInfo.nickname(),        // 갱신 (닉네임은 바뀔 수 있음)
                this.role,                   // 유지 (권한은 서버에서 관리)
                this.statusMessage,
                oauthInfo.oauthRefreshToken() // 갱신 (새로 발급된 소셜 리프레시 토큰)
        );
    }

    /**
     * 프로필 정보 수정 (닉네임, 상태 메시지)
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