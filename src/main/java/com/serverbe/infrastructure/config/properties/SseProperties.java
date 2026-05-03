package com.serverbe.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml의 'app.sse' 설정을 매핑합니다.
 */
@ConfigurationProperties(prefix = "sse")
public record SseProperties(
        Long timeout, // 밀리초(ms) 단위
        String channel
) {}