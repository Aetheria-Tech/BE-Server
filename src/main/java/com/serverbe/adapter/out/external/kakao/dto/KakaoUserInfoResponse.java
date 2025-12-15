package com.serverbe.adapter.out.external.kakao.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// 2. 유저 정보 요청 응답
public record KakaoUserInfoResponse(
    Long id,
    @JsonProperty("kakao_account") KakaoAccount kakaoAccount
) {
    public record KakaoAccount(
        String email,
        Profile profile
    ) {
        public record Profile(String nickname) {}
    }
}