package com.serverbe.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "art")
public record ArtProperties(
        double maxRadius,
        int maxResultLimit
) {
}
