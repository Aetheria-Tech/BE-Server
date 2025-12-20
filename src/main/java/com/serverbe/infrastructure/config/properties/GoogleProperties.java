package com.serverbe.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "google")
public record GoogleProperties(
        Auth auth
) {
    public record Auth(
            String clientId,
            String clientSecret,
            String redirectUri,
            String oauthApi,
            String api
    ) {
    }
}