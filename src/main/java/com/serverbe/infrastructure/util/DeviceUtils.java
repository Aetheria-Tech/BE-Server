package com.serverbe.infrastructure.util;

import com.serverbe.domain.exception.server.ServerErrorCode;
import com.serverbe.domain.exception.server.ServerException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class DeviceUtils {

    private static final String DEVICE_ID_HEADER = "X-Device-Id";
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final String APP_VERSION_HEADER = "X-App-Version"; // 클라이언트 버전 헤더 추가

    /**
     * 요청 헤더에서 Device ID를 추출합니다.
     * 1순위: X-Device-Id 헤더 (모바일 앱 등에서 명시적으로 보낸 경우)
     * 2순위: User-Agent 기반 해시 (웹 브라우저인 경우)
     *
     * @throws ServerException Device ID를 식별할 수 없는 경우
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
            return encodeUserAgentToBase64(userAgent);
        }

        // 3. 식별 불가
        throw new ServerException(
                ServerErrorCode.DE_IDENTIFIED_DEVICES,
                "Device ID를 식별할 수 없습니다. X-Device-Id 또는 User-Agent 헤더를 확인해주세요."
        );
    }


    public static String encodeUserAgentToBase64(String userAgent) {
        return Base64.getEncoder().encodeToString(userAgent.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param request 현재 HTTP 요청 객체
     * @return 추출된 클라이언트 버전 문자열 또는 "UNKNOWN_VERSION"
     * @responsibility HTTP 요청 헤더에서 클라이언트/앱 버전을 추출합니다.
     * @implNote X-App-Version 헤더를 우선적으로 확인하고, 없으면 "UNKNOWN_VERSION"을 반환합니다.
     */
    public static String extractAppVersion(HttpServletRequest request) {
        String appVersion = request.getHeader(APP_VERSION_HEADER);
        if (StringUtils.hasText(appVersion)) {
            return appVersion;
        }
        return "UNKNOWN_VERSION"; // 헤더가 없을 경우 기본값 반환
    }
}