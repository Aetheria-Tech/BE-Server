package com.serverbe.infrastructure.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

public class ClientIpUtils {

    public static final String UNKNOWN_IP = "UNKNOWN";

    private static final String[] IP_HEADER_CANDIDATES = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "X-Real-IP"
    };

    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN_IP;
        }

        // 1. 헤더 검사
        for (String header : IP_HEADER_CANDIDATES) {
            String ip = request.getHeader(header);

            if (isValidIp(ip)) {
                return extractClientIp(ip);
            }
        }

        // 2. 헤더에 없으면 RemoteAddr 확인
        String remoteAddr = request.getRemoteAddr();
        if (isValidIp(remoteAddr)) {
            return remoteAddr;
        }

        // 3. 끝까지 못 찾으면 UNKNOWN 반환
        return UNKNOWN_IP;
    }

    private static boolean isValidIp(String ip) {
        return StringUtils.hasText(ip)
                && !UNKNOWN_IP.equalsIgnoreCase(ip)
                && !"0:0:0:0:0:0:0:1".equals(ip);
    }

    private static String extractClientIp(String ip) {
        if (ip.contains(",")) {
            return ip.split(",")[0].trim();
        }
        return ip;
    }
}