package com.serverbe.application.service;

import com.serverbe.application.port.in.ratelimit.RateLimitUseCase;
import com.serverbe.application.port.out.ratelimit.RateLimitPort;
import com.serverbe.infrastructure.config.properties.RateLimitProperties;
import org.springframework.stereotype.Service;

@Service
public class RateLimiterService implements RateLimitUseCase {

    private final RateLimitPort rateLimitPort;

    private final int userCapacity;
    private final int userRefillRate;
    private final int ipCapacity;
    private final int ipRefillRate;
    private final String userPrefix;
    private final String ipPrefix;

    public RateLimiterService(
            RateLimitPort rateLimitPort,
            RateLimitProperties rateLimitProperties
    ) {
        this.rateLimitPort = rateLimitPort;

        // Properties 매핑
        this.userCapacity = rateLimitProperties.user().capacity();
        this.userRefillRate = rateLimitProperties.user().refillRate();
        this.ipCapacity = rateLimitProperties.ip().capacity();
        this.ipRefillRate = rateLimitProperties.ip().refillRate();
        this.userPrefix = rateLimitProperties.prefix().user();
        this.ipPrefix = rateLimitProperties.prefix().ip();
    }

    public boolean isAllowedForUser(Long userId) {
        String key = userPrefix + userId;
        return rateLimitPort.isAllowed(key, userCapacity, userRefillRate);
    }

    public boolean isAllowedForIp(String ip) {
        String key = ipPrefix + ip;
        return rateLimitPort.isAllowed(key, ipCapacity, ipRefillRate);
    }
}