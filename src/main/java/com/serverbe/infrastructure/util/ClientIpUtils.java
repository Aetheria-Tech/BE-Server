package com.serverbe.infrastructure.util;

import jakarta.servlet.http.HttpServletRequest;

public class ClientIpUtils {

    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        // X-Forwarded-For에 여러 IP가 있는 경우 첫 번째가 실제 클라이언트 IP
        if (ip != null && ip.contains(",")) {
            return ip.split(",")[0].trim();
        }
        
        return ip;
    }
}