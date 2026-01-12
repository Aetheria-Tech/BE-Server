package com.serverbe.adapter.in.web.filter;


import com.serverbe.application.port.out.security.TokenResolver;
import com.serverbe.application.port.out.token.TokenPersistencePort;
import com.serverbe.domain.exception.BusinessException;
import com.serverbe.domain.exception.auth.AuthErrorCode;
import com.serverbe.domain.exception.auth.AuthException;
import com.serverbe.infrastructure.security.TokenExtractor;
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
 * @responsibility 모든 HTTP 요청의 <b>인증 헤더</b>를 검사하여 유효한 사용자일 경우 <b>SecurityContext</b>에 인증 정보를 등록합니다.
 * @implSpec {@link OncePerRequestFilter}를 확장하여 요청당 한 번의 실행을 보장하며, 블랙리스트 확인을 통한 <b>Fail-Fast</b> 보안 전략을 수행합니다.
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenResolver tokenResolver;
    private final TokenPersistencePort tokenPersistencePort;
    private final TokenExtractor tokenExtractor;
    private final HandlerExceptionResolver handlerExceptionResolver;

    /**
     * @implNote 필터에서 발생하는 예외를 스프링 MVC의 예외 처리 메커니즘으로 전달하기 위해 {@link HandlerExceptionResolver}를 주입받습니다.
     */
    public JwtAuthenticationFilter(
            TokenResolver tokenResolver,
            TokenPersistencePort tokenPersistencePort,
            TokenExtractor tokenExtractor,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver
    ) {
        this.tokenResolver = tokenResolver;
        this.tokenPersistencePort = tokenPersistencePort;
        this.handlerExceptionResolver = handlerExceptionResolver;
        this.tokenExtractor = tokenExtractor;
    }

    /**
     * @param request     HTTP 요청 객체
     * @param response    HTTP 응답 객체
     * @param filterChain 서블릿 필터 체인
     * @throws BusinessException 토큰이 블랙리스트에 등록되어 있거나 인증 과정에서 문제가 발생할 경우 발생
     * @responsibility 요청에서 토큰을 추출하고, 유효성 검증 및 블랙리스트 대조 후 인증 객체를 등록합니다.
     * @implSpec 1. <b>토큰 추출</b>: {@link TokenExtractor}를 통해 헤더에서 Bearer 토큰을 가져옵니다.<br>
     * 2. <b>블랙리스트 검사</b>: 유효한 토큰이라도 이미 로그아웃된 토큰인지 {@link TokenPersistencePort#isBlacklisted(String)}로 확인합니다.<br>
     * 3. <b>인증 등록</b>: 모든 검증이 완료되면 {@link SecurityContextHolder}에 인증 정보를 저장합니다.
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) {
        String token = tokenExtractor.extractAccessToken(request);

        try {
            if (StringUtils.hasText(token) && tokenResolver.validateAccessToken(token)) {

                // 1. 블랙리스트 확인을 인증 객체 등록보다 먼저 수행 (Fail-Fast)
                if (tokenPersistencePort.isBlacklisted(token)) {
                    log.warn("[JWT Filter] 로그아웃된 토큰으로 접근 시도: {}", token);
                    throw new AuthException(AuthErrorCode.JWT_TOKEN_IS_LOGOUT);
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