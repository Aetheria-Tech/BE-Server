package com.serverbe.infrastructure.config.aop;

import com.serverbe.application.annotation.RateLimit;
import com.serverbe.application.port.out.security.TokenResolver;
import com.serverbe.application.service.RateLimiterService;
import com.serverbe.domain.exception.auth.AuthErrorCode;
import com.serverbe.domain.exception.auth.AuthException;
import com.serverbe.domain.exception.server.RateLimitExceededException;
import com.serverbe.domain.exception.server.ServerErrorCode;
import com.serverbe.infrastructure.security.TokenExtractor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RateLimitAspectTest {

    @InjectMocks
    private RateLimitAspect rateLimitAspect;

    @Mock
    private RateLimiterService rateLimiterService;

    @Mock
    private TokenResolver tokenResolver;

    @Mock
    private TokenExtractor tokenExtractor;

    @Mock
    private ProceedingJoinPoint joinPoint; // AOP 진행을 위한 Mock

    private MockHttpServletRequest request;
    private RateLimit rateLimit; // Mock Annotation

    private static final String TEST_ENDPOINT = "/api/v1/test";

    @BeforeEach
    void setUp() {
        // 1. Mock Request 세팅 및 URI 지정
        request = new MockHttpServletRequest();
        request.setRequestURI(TEST_ENDPOINT);

        // 2. RequestContextHolder에 Request 주입 (AOP 내부에서 꺼내 쓸 수 있도록)
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // 3. Annotation Mocking 준비
        rateLimit = mock(RateLimit.class);
    }

    @AfterEach
    void tearDown() {
        // 다른 테스트에 영향을 주지 않도록 초기화
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("유효한 토큰이 있고 User 기반 허용량 이내라면 통과한다")
    void checkRateLimit_User_Allowed_Pass() throws Throwable {
        // given
        String accessToken = "valid_token";
        Long userId = 100L;
        int capacity = 10;
        int refillRate = 1;

        // 애노테이션 설정 Mocking
        given(rateLimit.target()).willReturn(RateLimit.TargetType.USER);
        given(rateLimit.capacity()).willReturn(capacity);
        given(rateLimit.refillRate()).willReturn(refillRate);

        given(tokenExtractor.extractAccessToken(request)).willReturn(accessToken);
        given(tokenResolver.getIdFromToken(accessToken)).willReturn(userId);

        // 서비스 허용 Mocking (파라미터 4개 모두 일치해야 함)
        given(rateLimiterService.isAllowedForUser(userId, TEST_ENDPOINT, capacity, refillRate)).willReturn(true);

        // 원래 메서드의 반환값 Mocking
        Object expectedResponse = new Object();
        given(joinPoint.proceed()).willReturn(expectedResponse);

        // when
        Object result = rateLimitAspect.checkRateLimit(joinPoint, rateLimit);

        // then
        assertThat(result).isEqualTo(expectedResponse);
        verify(rateLimiterService).isAllowedForUser(userId, TEST_ENDPOINT, capacity, refillRate);
    }

    @Test
    @DisplayName("User 기반 제한인데 토큰이 없다면 AuthException이 발생한다")
    void checkRateLimit_User_NoToken_ThrowsAuthException() {
        // given
        given(rateLimit.target()).willReturn(RateLimit.TargetType.USER);
        given(tokenExtractor.extractAccessToken(request)).willReturn(null); // 토큰 없음

        // when & then
        assertThatThrownBy(() -> rateLimitAspect.checkRateLimit(joinPoint, rateLimit))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.JWT_TOKEN_IS_EMPTY);
    }

    @Test
    @DisplayName("User 기반 제한 초과 시 RateLimitExceededException이 발생한다")
    void checkRateLimit_User_Blocked_ThrowsException() {
        // given
        String accessToken = "valid_token";
        Long userId = 100L;
        int capacity = 10;
        int refillRate = 1;
        int retryAfter = 60; // 60초 대기

        given(rateLimit.target()).willReturn(RateLimit.TargetType.USER);
        given(rateLimit.capacity()).willReturn(capacity);
        given(rateLimit.refillRate()).willReturn(refillRate);
        given(rateLimit.retryAfterSeconds()).willReturn(retryAfter);

        given(tokenExtractor.extractAccessToken(request)).willReturn(accessToken);
        given(tokenResolver.getIdFromToken(accessToken)).willReturn(userId);

        // 차단 상황 설정 (isAllowed -> false)
        given(rateLimiterService.isAllowedForUser(userId, TEST_ENDPOINT, capacity, refillRate)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> rateLimitAspect.checkRateLimit(joinPoint, rateLimit))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(exception -> {
                    RateLimitExceededException e = (RateLimitExceededException) exception;
                    assertThat(e.getErrorCode()).isEqualTo(ServerErrorCode.TOO_MANY_REQUESTS);
                    assertThat(e.getRetryAfterSeconds()).isEqualTo(retryAfter); // 헤더에 들어갈 시간 검증
                });
    }

    @Test
    @DisplayName("허용된 IP라면 통과한다")
    void checkRateLimit_Ip_Allowed_Pass() throws Throwable {
        // given
        String clientIp = "127.0.0.1";
        int capacity = 5;
        int refillRate = 1;

        // 1. request 객체에 값 세팅
        request.addHeader("X-Forwarded-For", clientIp);
        request.setRemoteAddr(clientIp);

        // ★ 2. 여기가 핵심입니다 ★
        // 우리가 세팅한 request를 현재 쓰레드의 RequestContextHolder에 등록합니다.
        // 이제 RateLimitAspect가 IP를 추출하려고 할 때 이 request를 꺼내보게 됩니다.
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // 3. Mockito 동작 설정
        given(rateLimit.target()).willReturn(RateLimit.TargetType.IP);
        given(rateLimit.capacity()).willReturn(capacity);
        given(rateLimit.refillRate()).willReturn(refillRate);

        given(rateLimiterService.isAllowedForIp(clientIp, TEST_ENDPOINT, capacity, refillRate)).willReturn(true);

        Object expectedResponse = new Object();
        given(joinPoint.proceed()).willReturn(expectedResponse);

        // when
        Object result = rateLimitAspect.checkRateLimit(joinPoint, rateLimit);

        // then
        assertThat(result).isEqualTo(expectedResponse);

        // (선택 사항) 다음 테스트에 영향을 주지 않도록 초기화
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("IP 기반 제한 초과 시 RateLimitExceededException이 발생한다")
    void checkRateLimit_Ip_Blocked_ThrowsException() {
        // given
        String clientIp = "127.0.0.1";
        request.addHeader("X-Forwarded-For", clientIp);
        request.setRemoteAddr(clientIp);
        int capacity = 5;
        int refillRate = 1;
        int retryAfter = 120; // 120초 대기

        given(rateLimit.target()).willReturn(RateLimit.TargetType.IP);
        given(rateLimit.capacity()).willReturn(capacity);
        given(rateLimit.refillRate()).willReturn(refillRate);
        given(rateLimit.retryAfterSeconds()).willReturn(retryAfter);

        // 차단 상황 설정 (isAllowed -> false)
        given(rateLimiterService.isAllowedForIp(clientIp, TEST_ENDPOINT, capacity, refillRate)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> rateLimitAspect.checkRateLimit(joinPoint, rateLimit))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(exception -> {
                    RateLimitExceededException e = (RateLimitExceededException) exception;
                    assertThat(e.getErrorCode()).isEqualTo(ServerErrorCode.TOO_MANY_REQUESTS);
                    assertThat(e.getRetryAfterSeconds()).isEqualTo(retryAfter);
                });
    }
}