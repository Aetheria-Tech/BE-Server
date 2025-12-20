package com.serverbe.application.port.in.oauth;

public interface LogoutUseCase {
    // 액세스 토큰을 사용하여 리프레쉬 토큰을 무효화한다.
    void logout(String accessToken, String refreshToken);

    // 액세스 토큰을 사용하여 모든 리프레쉬 토큰을 무효화한다
    void globalLogout(String accessToken);
}