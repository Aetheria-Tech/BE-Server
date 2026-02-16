package com.serverbe.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "limit-rate")
public record RateLimitProperties(
        User user,
        Ip ip,
        Prefix prefix
) {
    /**
     * 인증된 사용자(User)에 대한 제한 정책
     * @param capacity 물통 크기 (최대 버스트 허용량)
     * @param refillRate 초당 충전 속도
     */
    public record User(
            int capacity,
            int refillRate
    ) {}

    /**
     * 비로그인(IP) 사용자에 대한 제한 정책
     * @param capacity 물통 크기 (최대 버스트 허용량)
     * @param refillRate 초당 충전 속도
     */
    public record Ip(
            int capacity,
            int refillRate
    ) {}

    /**
     * Redis Key 생성 시 사용할 접두사(Prefix) 설정
     */
    public record Prefix(
            String user,
            String ip
    ) {}
}