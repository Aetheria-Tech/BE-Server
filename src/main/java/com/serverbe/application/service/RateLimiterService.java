package com.serverbe.application.service;

import com.serverbe.application.port.in.ratelimit.RateLimitUseCase;
import com.serverbe.application.port.out.ratelimit.RateLimitPort;
import com.serverbe.application.service.fallback.RateLimitFallbackHandler;
import com.serverbe.infrastructure.config.properties.RateLimitProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RateLimiterService implements RateLimitUseCase {

    private final RateLimitPort rateLimitPort;
    private final RateLimitFallbackHandler fallbackHandler;
    private final String rateLimitUserPrefix;
    private final String rateLimitIpPrefix;

    public RateLimiterService(
            RateLimitPort rateLimitPort,
            RateLimitProperties rateLimitProperties,
            RateLimitFallbackHandler fallbackHandler
    ) {
        this.rateLimitPort = rateLimitPort;
        this.fallbackHandler = fallbackHandler;

        this.rateLimitUserPrefix = rateLimitProperties.prefix().user();
        this.rateLimitIpPrefix = rateLimitProperties.prefix().ip();
    }

    @Override
    @CircuitBreaker(name = "redisRateLimit", fallbackMethod = "fallbackForUser")
    public boolean isAllowedForUser(Long userId, String endpoint, int capacity, int refillRate) {
        String key = rateLimitUserPrefix + userId + ":" + endpoint;
        return rateLimitPort.isAllowed(key, capacity, refillRate);
    }

    public boolean fallbackForUser(Long userId, String endpoint, int capacity, int refillRate, Throwable t) {
        return fallbackHandler.handleUserFallback(userId, endpoint, capacity, refillRate, rateLimitUserPrefix, t);
    }

    @Override
    @CircuitBreaker(name = "redisRateLimit", fallbackMethod = "fallbackForIp")
    public boolean isAllowedForIp(String ip, String endpoint, int ipCapacity, int ipRefillRate) {
        String key = rateLimitIpPrefix + ip + ":" + endpoint;
        return rateLimitPort.isAllowed(key, ipCapacity, ipRefillRate);
    }

    public boolean fallbackForIp(String ip, String endpoint, int ipCapacity, int ipRefillRate, Throwable t) {
        return fallbackHandler.handleIpFallback(ip, endpoint, ipCapacity, ipRefillRate, t);
    }
}