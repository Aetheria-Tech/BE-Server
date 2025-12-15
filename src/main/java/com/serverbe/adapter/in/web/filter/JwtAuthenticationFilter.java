package com.serverbe.adapter.in.web.filter;


import com.serverbe.application.port.in.security.TokenResolver;
import com.serverbe.application.port.out.TokenPersistencePort;
import com.serverbe.infrastructure.config.properties.JwtProperties;
import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 모든 HTTP 요청에서 JWT 토큰을 검사하고 유효한 경우 인증 정보를 SecurityContext에 저장합니다.
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenResolver tokenResolver;
    private final TokenPersistencePort tokenPersistencePort;
    private final HandlerExceptionResolver handlerExceptionResolver;
    private final String ACCESS_TOKEN_HEADER;

    public JwtAuthenticationFilter(
            TokenResolver tokenResolver,
            TokenPersistencePort tokenPersistencePort,
            HandlerExceptionResolver handlerExceptionResolver,
            JwtProperties jwtProperties
    ) {
        this.tokenResolver = tokenResolver;
        this.tokenPersistencePort = tokenPersistencePort;
        this.handlerExceptionResolver = handlerExceptionResolver;
        this.ACCESS_TOKEN_HEADER = jwtProperties.accessToken().header();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) {

        // 요청 헤더에서 토큰 추출
        String token = resolveToken(request);

        try {
            // 토큰 유효성 검사
            if (StringUtils.hasText(token) && tokenResolver.validateToken(token)) {
                // 토큰에서 인증 객체(Authentication) 생성 및 SecurityContext 등록
                Authentication authentication = tokenResolver.getAuthentication(token);
                SecurityContextHolder.getContext().setAuthentication(authentication);

                if (tokenPersistencePort.isBlacklisted(token)) {
                    throw new BusinessException(ErrorMessage.UNAUTHORIZED, "이미 로그아웃된 토큰입니다.");
                }
            }
            // 다음 필터로 진행
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            // 필터 내 예외 발생 시 HandlerExceptionResolver를 통해 GlobalExceptionHandler로 전달
            log.error("[JWT Filter Exception] -> ", e);
            handlerExceptionResolver.resolveException(request, response, null, e);
        }
    }

    /**
     * HTTP 요청 헤더에서 JWT 토큰을 추출합니다.
     */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(ACCESS_TOKEN_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}