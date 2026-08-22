package com.serverbe.application.service.helper;

import com.serverbe.application.port.out.dto.oauth.OAuthUserInfoResult;
import com.serverbe.application.port.out.jpa.UserRepositoryPort;
import com.serverbe.domain.model.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserDataSyncManager {

    private final UserRepositoryPort userRepositoryPort;

    /**
     * @responsibility OAuth 정보를 바탕으로 기존 유저 정보를 갱신하거나 신규 유저를 생성합니다. (Upsert)
     */
    @Transactional
    public User syncUserByOAuth(OAuthUserInfoResult oauthInfo) {
        return userRepositoryPort.findByOauthId(oauthInfo.oauthId(), oauthInfo.provider())
                .map(existingUser -> {
                    log.info("[LOGIN] 기존 회원 접속: ID={}, Provider={}", existingUser.id(), oauthInfo.provider());
                    return userRepositoryPort.save(existingUser.updateFromOAuth(
                            oauthInfo.email(), oauthInfo.nickname(), oauthInfo.oauthRefreshToken()
                    ));
                })
                .orElseGet(() -> {
                    User newUser = userRepositoryPort.save(User.createNew(
                            oauthInfo.oauthId(), oauthInfo.provider(), oauthInfo.email(), oauthInfo.nickname(), oauthInfo.oauthRefreshToken()
                    ));
                    log.info("[REGISTER] 신규 회원 가입 완료: ID={}, Provider={}", newUser.id(), oauthInfo.provider());
                    return newUser;
                });
    }
}