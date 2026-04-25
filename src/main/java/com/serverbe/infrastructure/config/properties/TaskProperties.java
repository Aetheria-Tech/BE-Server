package com.serverbe.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "task")
public record TaskProperties(
        int taskTimeoutThresholdMinutes
) {
}