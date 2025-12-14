package com.serverbe.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis 관련 설정 정보 객체 (Record)
 * @param prefix 레디스 키 접두어 (예: runner)
 * @param suffix 레디스 키 접미어 (예: token_rts)
 * @param maxToken 최대로 발급 가능한 토큰의 수
 */
@ConfigurationProperties(prefix = "redis")
public record RedisProperties(
    String prefix,
    String suffix,
    int maxToken
) {
}