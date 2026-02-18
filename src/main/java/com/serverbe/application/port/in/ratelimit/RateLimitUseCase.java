package com.serverbe.application.port.in.ratelimit;

public interface RateLimitUseCase {
    boolean isAllowedForUser(Long userId);
    boolean isAllowedForIp(String ip);
}