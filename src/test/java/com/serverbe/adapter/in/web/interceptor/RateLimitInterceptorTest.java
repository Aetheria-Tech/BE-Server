package com.serverbe.adapter.in.web.interceptor;

import com.serverbe.application.port.out.security.TokenResolver;
import com.serverbe.application.service.RateLimiterService;
import com.serverbe.infrastructure.security.TokenExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @InjectMocks
    private RateLimitInterceptor rateLimitInterceptor;

    @Mock
    private RateLimiterService rateLimiterService;

    @Mock
    private TokenResolver tokenResolver;

    @Mock
    private TokenExtractor tokenExtractor;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("Preflight(OPTIONS) 요청은 제한 없이 통과한다")
    void preHandle_Preflight_Pass() throws Exception {
        // given
        request.setMethod("OPTIONS");
        // [수정] CorsUtils가 Preflight로 인식하기 위해 필수 헤더 추가
        request.addHeader("Origin", "http://localhost:3000");
        request.addHeader("Access-Control-Request-Method", "GET");

        // when
        boolean result = rateLimitInterceptor.preHandle(request, response, new Object());

        // then
        assertThat(result).isTrue();

        // 검증: RateLimiterService는 호출되지 않아야 함
        verify(rateLimiterService, never()).isAllowedForUser(anyLong());
        verify(rateLimiterService, never()).isAllowedForIp(anyString());
    }

    @Test
    @DisplayName("유효한 토큰이 있는 경우 User ID 기반으로 체크하고 통과한다")
    void preHandle_ValidToken_UserCheck_Pass() throws Exception {
        // given
        String accessToken = "valid_token";
        Long userId = 100L;

        given(tokenExtractor.extractAccessToken(request)).willReturn(accessToken);
        given(tokenResolver.validateAccessToken(accessToken)).willReturn(true);
        given(tokenResolver.getIdFromToken(accessToken)).willReturn(userId);
        given(rateLimiterService.isAllowedForUser(userId)).willReturn(true); // 허용

        // when
        boolean result = rateLimitInterceptor.preHandle(request, response, new Object());

        // then
        assertThat(result).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        verify(rateLimiterService).isAllowedForUser(userId);
    }

    @Test
    @DisplayName("토큰이 없는 경우 IP 기반으로 체크하고 통과한다")
    void preHandle_NoToken_IpCheck_Pass() throws Exception {
        // given
        String clientIp = "127.0.0.1";
        request.setRemoteAddr(clientIp); // IP 설정

        given(tokenExtractor.extractAccessToken(request)).willReturn(null);
        // 토큰이 없으므로 validateToken 호출 안됨 -> 바로 IP 체크
        given(rateLimiterService.isAllowedForIp(clientIp)).willReturn(true);

        // when
        boolean result = rateLimitInterceptor.preHandle(request, response, new Object());

        // then
        assertThat(result).isTrue();
        verify(rateLimiterService).isAllowedForIp(clientIp);
    }

    @Test
    @DisplayName("User 기반 제한 초과 시 429 상태 코드를 반환하고 요청을 막는다")
    void preHandle_UserCheck_Blocked() throws Exception {
        // given
        String accessToken = "valid_token";
        Long userId = 100L;

        given(tokenExtractor.extractAccessToken(request)).willReturn(accessToken);
        given(tokenResolver.validateAccessToken(accessToken)).willReturn(true);
        given(tokenResolver.getIdFromToken(accessToken)).willReturn(userId);
        given(rateLimiterService.isAllowedForUser(userId)).willReturn(false); // 차단!!

        // when
        boolean result = rateLimitInterceptor.preHandle(request, response, new Object());

        // then
        assertThat(result).isFalse(); // 컨트롤러 진입 불가
        assertThat(response.getStatus()).isEqualTo(429); // Too Many Requests
        assertThat(response.getHeader("Retry-After")).isEqualTo("1");
    }

    @Test
    @DisplayName("IP 기반 제한 초과 시 429 상태 코드를 반환하고 요청을 막는다")
    void preHandle_IpCheck_Blocked() throws Exception {
        // given
        String clientIp = "127.0.0.1";
        request.setRemoteAddr(clientIp);

        given(tokenExtractor.extractAccessToken(request)).willReturn(null);
        given(rateLimiterService.isAllowedForIp(clientIp)).willReturn(false); // 차단!!

        // when
        boolean result = rateLimitInterceptor.preHandle(request, response, new Object());

        // then
        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
    }
}