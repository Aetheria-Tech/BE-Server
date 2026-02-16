package com.serverbe.infrastructure.util;

import com.serverbe.domain.exception.server.ServerErrorCode;
import com.serverbe.domain.exception.server.ServerException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

public final class NetworkUtils {

    private static final String[] IP_HEADER_CANDIDATES = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA"
    };

    private NetworkUtils() {
        throw new ServerException(ServerErrorCode.UTILITY_CLASS, "Utility Class");
    }

    public static String getClientIp(HttpServletRequest request) {
        for (String header : IP_HEADER_CANDIDATES) {
            String ipList = request.getHeader(header);
            if (StringUtils.hasText(ipList) && !"unknown".equalsIgnoreCase(ipList)) {
                // X-Forwarded-For 헤더는 "client, proxy1, proxy2" 형태일 수 있으므로 첫 번째 IP를 추출
                return ipList.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}