package com.serverbe.application.service;

import com.serverbe.application.port.in.oauth.OAuthClientPort;
import com.serverbe.application.port.in.oauth.WithdrawUseCase;
import com.serverbe.application.port.out.TokenPersistencePort;
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
    private final TokenPersistencePort tokenPersistencePort;

    @Override
    @Transactional
    public void withdraw(Long userId) {
        // 사용자 조회
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorMessage.NOT_FOUND_RUNNER));

        // 적절한 OAuthClient 찾기
        OAuthClientPort client = getClient(user.provider());

        // 소셜 서비스 연동 해제 요청 (unlink)
        client.unlink(user.provider(), user.oauthId(), user.oauthRefreshToken());

        // 우리 DB에서 사용자 삭제 (Hard Delete)
        userRepositoryPort.deleteById(userId);

        // Redis에서 리프레쉬 토큰 삭제
        tokenPersistencePort.deleteRefreshToken(userId);
    }

    // Provider에 맞는 구현체를 찾는 헬퍼 메서드
    private OAuthClientPort getClient(OAuthProvider provider) {
        return oAuthClients.stream()
                .filter(client -> client.supports(provider))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorMessage.INTERNAL_SERVER_ERROR, "지원하지 않는 소셜 로그인입니다."));
    }
}