package com.serverbe.application.service;

import com.serverbe.application.port.out.oauth.OAuthClientPort;
import com.serverbe.application.port.in.jpa.UserRepositoryPort;
import com.serverbe.domain.model.User;
import com.serverbe.domain.model.vo.OAuthProvider;
import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SocialTokenService {

    private final UserRepositoryPort userRepositoryPort;
    private final List<OAuthClientPort> oAuthClients;

    /**
     * 특정 유저의 유효한 소셜 Access Token을 발급받습니다.
     * 필요시 Refresh Token을 사용하여 갱신하며, 변경된 정보는 DB에 저장합니다.
     */
    @Transactional
    public Mono<String> getFreshAccessToken(Long userId) {
        // 1. 유저 조회 (암호화된 토큰은 Converter에 의해 자동 복호화됨)
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorMessage.NOT_FOUND_RUNNER));

        // 2. 유저의 제공자(KAKAO, GOOGLE)를 지원하는 어댑터 선택
        OAuthClientPort client = getClient(user.provider());

        // 3. 찾은 어댑터로 토큰 갱신 요청
        return client.refreshSocialToken(
                user.provider(),
                user.oauthRefreshToken()
        ).map(response -> {
            if(response.refreshToken() != null) {
                User updatedUser = user.renewOauthRefreshToken(response.refreshToken());
                userRepositoryPort.save(updatedUser);
            }
            return response.accessToken();
        });
    }

    /**
     * Strategy Pattern: 지원하는 Provider를 확인하여 알맞은 클라이언트를 반환
     */
    private OAuthClientPort getClient(OAuthProvider provider) {
        return oAuthClients.stream()
                .filter(client -> client.supports(provider)) // 클래스 이름이 아닌 명시적 지원 여부 확인
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorMessage.INTERNAL_SERVER_ERROR, "지원하지 않는 소셜 로그인입니다."));
    }
}