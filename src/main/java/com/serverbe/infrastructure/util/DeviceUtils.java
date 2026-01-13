package com.serverbe.infrastructure.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public class DeviceUtils {

    private static final String DEVICE_ID_HEADER = "X-Device-Id";
    private static final String USER_AGENT_HEADER = "User-Agent";

    /**
     * 요청 헤더에서 Device ID를 추출합니다.
     * 1순위: X-Device-Id 헤더 (모바일 앱 등에서 명시적으로 보낸 경우)
     * 2순위: User-Agent 기반 해시 (웹 브라우저인 경우)
     * 3순위: 알 수 없음 (UNKNOWN)
     */
    public static String extractDeviceId(HttpServletRequest request) {
        // 1. 커스텀 헤더 확인
        String deviceId = request.getHeader(DEVICE_ID_HEADER);
        if (StringUtils.hasText(deviceId)) {
            return deviceId;
        }

        // 2. User-Agent 확인 및 해싱
        String userAgent = request.getHeader(USER_AGENT_HEADER);
        if (StringUtils.hasText(userAgent)) {
            return generateHashFromUserAgent(userAgent);
        }

        // 3. 식별 불가
        return "UNKNOWN-DEVICE-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // User-Agent는 너무 길고 특수문자가 많으므로 Base64나 해시로 변환하여 깔끔한 ID로 만듭니다.
    private static String generateHashFromUserAgent(String userAgent) {
        return Base64.getEncoder().encodeToString(userAgent.getBytes(StandardCharsets.UTF_8));
    }
}