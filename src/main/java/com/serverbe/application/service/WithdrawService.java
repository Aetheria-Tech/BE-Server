package com.serverbe.application.service;

import com.serverbe.application.port.in.oauth.OAuthClientPort;
import com.serverbe.application.port.in.oauth.WithdrawUseCase;
import com.serverbe.application.port.out.UserRepositoryPort;
import com.serverbe.domain.model.User;
import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WithdrawService implements WithdrawUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final OAuthClientPort oAuthClientPort;

    @Override
    @Transactional
    public void withdraw(Long userId) {
        // 1. 사용자 조회 (복호화된 이메일과 리프레시 토큰이 포함된 상태)
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorMessage.NOT_FOUND_RUNNER));

        // 2. 소셜 서비스 연동 해제 (카카오/구글 API 호출)
        // 저장되어 있던 oauthRefreshToken을 사용하여 연결을 끊습니다.
        oAuthClientPort.unlink(user.provider(), user.oauthRefreshToken(), user.oauthRefreshToken());

        // 3. 우리 DB에서 사용자 삭제 (Hard Delete)
        userRepositoryPort.deleteById(userId);
        
        // 4. (선택 사항) Redis에 저장된 우리 서비스 리프레시 토큰도 삭제
        // tokenRepository.deleteByUserId(userId);
    }
}