package com.serverbe.adapter.out.external.google.dto;

public record GoogleUserInfoResponse(
    String sub, // 구글의 고유 식별자 (oauthId)
    String email,
    String name,
    String picture
) { }