package com.serverbe.application.port.in.oauth;

public interface LogoutUseCase {
    /**
     * @requirement  UC-AUTH-02 현재 기기 로그아웃
     * @param accessToken 사용자가 로그아웃에 사용한 액세스 토큰
     * @param refreshToken 사용자가 로그아웃을 요청한 리프레시 토큰
     * */
    void logout(String accessToken, String refreshToken);

    /**
     * @requirement UC-AUTH-03 모든 기기 로그아웃
     * @param accessToken 사용자가 로그아웃에 사용한 액세스 토큰
     * */
    void globalLogout(String accessToken);
}