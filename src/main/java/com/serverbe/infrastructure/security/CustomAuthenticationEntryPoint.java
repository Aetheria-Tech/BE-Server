package com.serverbe.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serverbe.infrastructure.common.response.RestApiResponse;
import com.serverbe.infrastructure.error.ErrorMessage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request, 
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        // ApiResponse.fail 형식을 그대로 유지하여 클라이언트에게 전달
        var apiResponse = RestApiResponse.fail(ErrorMessage.UNAUTHORIZED, "인증이 필요합니다.");
        
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}