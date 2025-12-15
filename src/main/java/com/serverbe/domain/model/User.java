package com.serverbe.domain.model;

import com.serverbe.domain.model.vo.OAuthProvider;
import com.serverbe.domain.model.vo.Role;

import java.util.List;

public record User(
        Long id,
        String oauthId,
        OAuthProvider provider,
        String email,
        String nickname,
        Role role,
        String oauthRefreshToken // 추가
) {
    /**
     * OAuth 리프레시 토큰 갱신 (불변 객체이므로 새 객체 반환)
     */
    public User renewOauthRefreshToken(String newToken) {
        return new User(id, oauthId, provider, email, nickname, role, newToken);
    }

    public List<String> getRoleList() {
        return List.of(role.name());
    }
}