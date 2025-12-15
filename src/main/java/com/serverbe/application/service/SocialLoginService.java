package com.serverbe.application.service;


import com.serverbe.adapter.out.external.google.GoogleAdapter;
import com.serverbe.adapter.out.external.kakao.KakaoAdapter;
import com.serverbe.application.port.in.dto.OAuthUserInfo;
import com.serverbe.application.port.in.dto.TokenResponse;
import com.serverbe.application.port.in.oauth.OAuthClientPort;
import com.serverbe.application.port.in.oauth.SocialLoginUseCase;
import com.serverbe.application.port.in.security.TokenProvider;
import com.serverbe.application.port.out.UserRepositoryPort;
import com.serverbe.domain.model.User;
import com.serverbe.domain.model.vo.OAuthProvider;
import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
@RequiredArgsConstructor
@Transactional
public class SocialLoginService implements SocialLoginUseCase {

    // 모든 OAuthClientPort 구현체를 주입받아 Map으로 관리
    private final List<OAuthClientPort> oAuthClients;
    private final UserRepositoryPort userRepositoryPort;
    private final TokenProvider tokenProvider;

    @Override
    public TokenResponse login(String code, OAuthProvider provider) {
        // 1. 외부 소셜 서버(카카오/구글)에서 유저 정보 가져오기
        OAuthClientPort client = oAuthClients.stream()
                .filter(c -> {
                    // 어댑터 내부에 "나는 KAKAO를 지원해"라는 메서드가 있으면 좋습니다.
                    // 여기선 단순히 클래스 이름으로 구분하거나 별도 메서드를 포트에 추가할 수 있습니다.
                    if (provider == OAuthProvider.KAKAO) return c instanceof KakaoAdapter;
                    if (provider == OAuthProvider.GOOGLE) return c instanceof GoogleAdapter;
                    return false;
                })
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorMessage.INTERNAL_SERVER_ERROR));

        OAuthUserInfo oauthInfo = client.getUserInfo(code, provider);

        // 2. DB에서 기존 유저인지 확인 (Upsert 로직)
        User user = userRepositoryPort.findByOauthId(oauthInfo.oauthId(), provider)
                .map(existingUser -> userRepositoryPort.save(existingUser.updateFromOAuth(oauthInfo)))
                .orElseGet(() -> userRepositoryPort.save(User.createNew(oauthInfo, provider)));

        // 3. 우리 서비스 전용 JWT 발급 (로그인 로직)
        return TokenResponse.of(
                tokenProvider.generateAccessToken(user.id(), user.role()),
                tokenProvider.generateRefreshToken(user.id(), user.role()),
                user.role()
        );
    }
}