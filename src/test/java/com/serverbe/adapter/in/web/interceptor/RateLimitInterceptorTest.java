package com.serverbe.adapter.in.web.interceptor;

import com.serverbe.application.port.out.security.TokenResolver;
import com.serverbe.application.service.RateLimiterService;
import com.serverbe.domain.exception.server.ServerErrorCode;
import com.serverbe.domain.exception.server.ServerException;
import com.serverbe.infrastructure.security.TokenExtractor;
import com.serverbe.infrastructure.util.ClientIpUtils;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

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
    void preHandle_Preflight_Pass() {
        // given
        request.setMethod("OPTIONS");
        request.addHeader("Origin", "http://localhost:3000");
        request.addHeader("Access-Control-Request-Method", "GET");

        // when
        boolean result = rateLimitInterceptor.preHandle(request, response, new Object());

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("유효한 토큰이 있고 허용된 유저라면 통과한다")
    void preHandle_ValidToken_Allowed() {
        // given
        String accessToken = "valid_token";
        Long userId = 100L;

        given(tokenExtractor.extractAccessToken(request)).willReturn(accessToken);
        given(tokenResolver.validateAccessToken(accessToken)).willReturn(true);
        given(tokenResolver.getIdFromToken(accessToken)).willReturn(userId);
        given(rateLimiterService.isAllowedForUser(userId)).willReturn(true);

        // when
        boolean result = rateLimitInterceptor.preHandle(request, response, new Object());

        // then
        assertThat(result).isTrue();
        verify(rateLimiterService).isAllowedForUser(userId);
    }

    @Test
    @DisplayName("토큰이 없고 허용된 IP라면 통과한다")
    void preHandle_NoToken_AllowedIp() {
        // given
        String clientIp = "127.0.0.1";
        // ClientIpUtils가 MockRequest에서 IP를 가져오도록 헤더 설정
        request.addHeader("X-Forwarded-For", clientIp);

        given(tokenExtractor.extractAccessToken(request)).willReturn(null);
        given(rateLimiterService.isAllowedForIp(clientIp)).willReturn(true);

        // when
        boolean result = rateLimitInterceptor.preHandle(request, response, new Object());

        // then
        assertThat(result).isTrue();
        verify(rateLimiterService).isAllowedForIp(clientIp);
    }

    @Test
    @DisplayName("User 기반 제한 초과 시 ServerException(TOO_MANY_REQUESTS)이 발생한다")
    void preHandle_User_Blocked_ThrowsException() {
        // given
        String accessToken = "valid_token";
        Long userId = 100L;

        given(tokenExtractor.extractAccessToken(request)).willReturn(accessToken);
        given(tokenResolver.validateAccessToken(accessToken)).willReturn(true);
        given(tokenResolver.getIdFromToken(accessToken)).willReturn(userId);

        // 차단 상황 설정
        given(rateLimiterService.isAllowedForUser(userId)).willReturn(false);

        // when & then
        // 예외가 발생하는지 검증 (GlobalExceptionHandler가 처리할 것이므로 여기선 예외 발생이 정상)
        assertThatThrownBy(() -> rateLimitInterceptor.preHandle(request, response, new Object()))
                .isInstanceOf(ServerException.class)
                .extracting("errorCode")
                .isEqualTo(ServerErrorCode.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("IP 기반 제한 초과 시 ServerException(TOO_MANY_REQUESTS)이 발생한다")
    void preHandle_Ip_Blocked_ThrowsException() {
        // given
        String clientIp = "127.0.0.1";
        request.addHeader("X-Forwarded-For", clientIp);

        given(tokenExtractor.extractAccessToken(request)).willReturn(null);

        // 차단 상황 설정
        given(rateLimiterService.isAllowedForIp(clientIp)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> rateLimitInterceptor.preHandle(request, response, new Object()))
                .isInstanceOf(ServerException.class)
                .extracting("errorCode")
                .isEqualTo(ServerErrorCode.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("IP 식별 불가(UNKNOWN) 시 로그를 남기고 UNKNOWN 키로 제한을 확인한다")
    void preHandle_UnknownIp_CheckedAsUnknown() {
        // given
        // MockHttpServletRequest는 기본값으로 "127.0.0.1"을 가집니다.
        // UNKNOWN 테스트를 위해 이를 강제로 비워줍니다.
        request.setRemoteAddr("");

        given(tokenExtractor.extractAccessToken(request)).willReturn(null);

        // 이제 ClientIpUtils는 IP를 찾지 못해 "UNKNOWN"을 반환할 것이고, 스터빙과 일치하게 됩니다.
        given(rateLimiterService.isAllowedForIp(ClientIpUtils.UNKNOWN_IP)).willReturn(true);

        // when
        boolean result = rateLimitInterceptor.preHandle(request, response, new Object());

        // then
        assertThat(result).isTrue();
        verify(rateLimiterService).isAllowedForIp(ClientIpUtils.UNKNOWN_IP);
    }
}