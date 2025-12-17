package com.serverbe.application.service;

import com.serverbe.application.port.in.oauth.OAuthClientPort;
import com.serverbe.application.port.in.oauth.WithdrawUseCase;
import com.serverbe.application.port.out.UserRepositoryPort;
import com.serverbe.domain.model.User;
import com.serverbe.domain.model.vo.OAuthProvider;
import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawService implements WithdrawUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final List<OAuthClientPort> oAuthClients;
    private final SocialTokenService socialTokenService;

    @Override
    @Transactional
    public void withdraw(Long userId) {
        // 사용자 조회
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorMessage.NOT_FOUND_RUNNER));
        log.info(user.toString());

        // 적절한 OAuthClient 찾기
        OAuthClientPort client = getClient(user.provider());

        // 소셜 연동 해제를 위한 최신 Access Token 확보
        // (저장된 리프레시 토큰으로 새 액세스 토큰을 받아옵니다. 만료 방지)
        String accessToken = socialTokenService.getFreshAccessToken(userId);

        // 소셜 서비스 연동 해제 요청 (unlink)
        client.unlink(user.provider(), user.oauthId(), accessToken);

        // 우리 DB에서 사용자 삭제 (Hard Delete)
        userRepositoryPort.deleteById(userId);
    }

    // Provider에 맞는 구현체를 찾는 헬퍼 메서드
    private OAuthClientPort getClient(OAuthProvider provider) {
        return oAuthClients.stream()
                .filter(client -> client.supports(provider))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorMessage.INTERNAL_SERVER_ERROR, "지원하지 않는 소셜 로그인입니다."));
    }
}