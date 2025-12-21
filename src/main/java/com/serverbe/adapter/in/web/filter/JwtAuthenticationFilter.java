package com.serverbe.adapter.in.web.filter;


import com.serverbe.application.port.out.security.TokenResolver;
import com.serverbe.application.port.in.redis.TokenPersistenceUseCase;
import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import com.serverbe.infrastructure.util.TokenExtractionUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private final TokenPersistenceUseCase tokenPersistenceUseCase;
    private final TokenExtractionUtils tokenExtractionUtils;
    private final HandlerExceptionResolver handlerExceptionResolver;

    public JwtAuthenticationFilter(
            TokenResolver tokenResolver,
            TokenPersistenceUseCase tokenPersistenceUseCase,
            TokenExtractionUtils tokenExtractionUtils,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver
    ) {
        this.tokenResolver = tokenResolver;
        this.tokenPersistenceUseCase = tokenPersistenceUseCase;
        this.handlerExceptionResolver = handlerExceptionResolver;
        this.tokenExtractionUtils = tokenExtractionUtils;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) {
        String token = tokenExtractionUtils.extractAccessToken(request);

        try {
            if (StringUtils.hasText(token) && tokenResolver.validateAccessToken(token)) {

                // 1. 블랙리스트 확인을 인증 객체 등록보다 먼저 수행 (Fail-Fast)
                if (tokenPersistenceUseCase.isBlacklisted(token)) {
                    log.warn("[JWT Filter] 로그아웃된 토큰으로 접근 시도: {}", token);
                    throw new BusinessException(ErrorMessage.UNAUTHORIZED, "이미 로그아웃된 토큰입니다.");
                }

                // 2. 모든 검증을 마친 후 인증 객체 등록
                Authentication authentication = tokenResolver.getAuthentication(token);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.error("[JWT Filter Exception] -> ", e);
            handlerExceptionResolver.resolveException(request, response, null, e);
        }
    }
}