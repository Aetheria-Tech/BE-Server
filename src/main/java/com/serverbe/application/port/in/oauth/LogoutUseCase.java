package com.serverbe.application.port.in.oauth;

public interface LogoutUseCase {
    void logout(String accessToken);
}