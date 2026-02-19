package com.serverbe.application.service;

import com.serverbe.application.port.in.ratelimit.RateLimitUseCase;
import com.serverbe.application.port.out.ratelimit.RateLimitPort;
import com.serverbe.infrastructure.config.properties.RateLimitProperties;
import org.springframework.stereotype.Service;

/**
 * 처리율 제한(Rate Limit) 비즈니스 로직을 처리하는 서비스 클래스입니다.
 * {@link RateLimitPort}를 통해 인프라 계층(Redis 등)과 통신하며,
 * 설정된 정책({@link RateLimitProperties})에 따라 요청 허용 여부를 결정합니다.
 */
@Service
public class RateLimiterService implements RateLimitUseCase {

    private final RateLimitPort rateLimitPort;

    // 비즈니스 설정 값들
    private final int userCapacity;
    private final int userRefillRate;
    private final int ipCapacity;
    private final int ipRefillRate;
    private final String userPrefix;
    private final String ipPrefix;

    /**
     * 의존성 주입 및 설정 파일에서 정책 값을 초기화합니다.
     */
    public RateLimiterService(
            RateLimitPort rateLimitPort,
            RateLimitProperties rateLimitProperties
    ) {
        this.rateLimitPort = rateLimitPort;

        // RateLimitProperties에서 설정 값을 로드하여 필드에 저장
        this.userCapacity = rateLimitProperties.user().capacity();
        this.userRefillRate = rateLimitProperties.user().refillRate();
        this.ipCapacity = rateLimitProperties.ip().capacity();
        this.ipRefillRate = rateLimitProperties.ip().refillRate();
        this.userPrefix = rateLimitProperties.prefix().user();
        this.ipPrefix = rateLimitProperties.prefix().ip();
    }

    /**
     * 인증된 사용자 ID 기반의 요청 허용 여부를 확인합니다.
     * @param userId 사용자 식별자
     * @return true(허용), false(차단)
     */
    @Override
    public boolean isAllowedForUser(Long userId) {
        String key = userPrefix + userId;
        return rateLimitPort.isAllowed(key, userCapacity, userRefillRate);
    }

    /**
     * IP 주소 기반의 요청 허용 여부를 확인합니다.
     * @param ip 클라이언트 IP 주소
     * @return true(허용), false(차단)
     */
    @Override
    public boolean isAllowedForIp(String ip) {
        String key = ipPrefix + ip;
        return rateLimitPort.isAllowed(key, ipCapacity, ipRefillRate);
    }
}