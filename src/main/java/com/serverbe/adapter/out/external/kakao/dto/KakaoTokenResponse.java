package com.serverbe.adapter.out.external.kakao.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// 1. 토큰 요청 응답
public record KakaoTokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("refresh_token") String refreshToken,
    @JsonProperty("expires_in") Integer expiresIn
) {}