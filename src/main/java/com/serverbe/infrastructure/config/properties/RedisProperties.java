package com.serverbe.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param host      레디스 호스트
 * @param port      레디스 포트
 * @param auth      인증 관련(Refresh Token) 설정
 * @param blacklist 로그아웃 관련(Blacklist) 설정
 */
@ConfigurationProperties(prefix = "redis")
public record RedisProperties(
        String host,
        int port,
        Auth auth,
        Blacklist blacklist
) {
    /**
     * 리프레시 토큰(RT) 저장 설정
     */
    public record Auth(
            String prefix,
            String suffix,
            int maxToken
    ) {
    }

    /**
     * 블랙리스트(BL) 저장 설정
     */
    public record Blacklist(
            String prefix,
            String suffix
    ) {
    }
}