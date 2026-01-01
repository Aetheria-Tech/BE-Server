package com.serverbe.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serverbe.infrastructure.common.response.ApiResponse;
import com.serverbe.infrastructure.error.ErrorMessage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        var apiResponse = ApiResponse.fail(ErrorMessage.ACCESS_DENIED, "해당 리소스에 대한 접근 권한이 없습니다.");

        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}