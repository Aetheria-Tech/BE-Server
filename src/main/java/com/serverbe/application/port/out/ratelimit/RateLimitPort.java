package com.serverbe.application.port.out.ratelimit;

public interface RateLimitPort {
    /**
     * @param key        Redis Key (예: "rate:user:1")
     * @param capacity   버킷 용량
     * @param refillRate 리필 속도
     * @return true(허용), false(차단)
     */
    boolean isAllowed(String key, int capacity, int refillRate);
}