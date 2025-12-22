package com.serverbe.adapter.in.web;

import com.serverbe.application.port.out.dto.oauth.AccessTokenResponse;
import com.serverbe.application.port.out.dto.oauth.TokenResponse;
import com.serverbe.application.port.in.oauth.LogoutUseCase;
import com.serverbe.application.port.in.oauth.LoginUseCase;
import com.serverbe.application.port.in.oauth.WithdrawUseCase;
import com.serverbe.application.port.in.token.ReissueUseCase;
import com.serverbe.domain.model.user.vo.OAuthProvider;
import com.serverbe.infrastructure.common.ApiResponse;
import com.serverbe.infrastructure.config.properties.JwtProperties;
import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import com.serverbe.infrastructure.util.TokenExtractionUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;

/**
 * 인증 및 권한 관련 HTTP 요청을 처리하는 웹 어댑터입니다.
 * 소셜 로그인 시작, 콜백 처리, 토큰 재발급, 로그아웃, 회원 탈퇴를 담당합니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final WithdrawUseCase withdrawUseCase;
    private final LogoutUseCase logoutUseCase;
    private final ReissueUseCase reissueUseCase;
    private final TokenExtractionUtils tokenExtractionUtils;
    private final String refreshTokenCookie;

    public AuthController(
            LoginUseCase loginUseCase,
            WithdrawUseCase withdrawUseCase,
            LogoutUseCase logoutUseCase,
            ReissueUseCase reissueUseCase,
            TokenExtractionUtils tokenExtractionUtils,
            JwtProperties jwtProperties
    ) {
        this.loginUseCase = loginUseCase;
        this.withdrawUseCase = withdrawUseCase;
        this.logoutUseCase = logoutUseCase;
        this.reissueUseCase = reissueUseCase;
        this.tokenExtractionUtils = tokenExtractionUtils;
        this.refreshTokenCookie = jwtProperties.refreshToken().cookie();
    }


    /**
     * 소셜 로그인 시작: 사용자를 소셜 서비스(카카오/구글)의 로그인 페이지로 보냅니다.
     *
     * @param provider KAKAO 또는 GOOGLE
     * @param response 리다이렉션을 위한 응답 객체
     */
    @GetMapping("/login/{provider}")
    public Mono<Void> redirectToSocial(
            @PathVariable(value = "provider") OAuthProvider provider,
            HttpServletResponse response
    ) {
        return Mono.fromCallable(() -> loginUseCase.getSocialLoginUrl(provider))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(redirectUrl -> {
                    try {
                        response.sendRedirect(redirectUrl);
                        return Mono.empty();
                    } catch (IOException e) {
                        return Mono.error(new BusinessException(ErrorMessage.INTERNAL_SERVER_ERROR, e.getMessage()));
                    }
                });
    }

    /**
     * 소셜 로그인 완료: 소셜 서버가 사용자 동의 후 백엔드로 인가 코드를 보내는 지점입니다.
     *
     * @param provider KAKAO 또는 GOOGLE
     * @param code     소셜 서버에서 발급한 일회성 인가 코드
     * @return 발급된 서비스 전용 JWT (Access, Refresh)
     */
    @GetMapping("/callback/{provider}")
    public Mono<ApiResponse<AccessTokenResponse>> loginCallback(
            @PathVariable(value = "provider") OAuthProvider provider,
            @RequestParam("code") String code,
            HttpServletResponse response
    ) {
        // 1. 인가 코드로 소셜 서버와 통신하여 유저 정보 획득
        // 2. 신규 유저면 가입, 기존 유저면 정보 업데이트(Upsert)
        // 3. 우리 서비스 전용 액세스/리프레시 토큰 발급 및 리프레시 토큰 Redis 저장
        return loginUseCase.login(code, provider)
                .map(tokenResponse -> {
                    addCookieToResponse(response, tokenResponse.refreshTokenResponse().opaqueToken(), 60 * 24 * 60 * 60);
                    return ApiResponse.success(tokenResponse.accessTokenResponse());
                });
    }

    /**
     * 회원 탈퇴: 사용자의 계정을 삭제하고 소셜 서비스와의 연동을 끊습니다.
     *
     * @param userId JwtAuthenticationFilter에 의해 SecurityContext에 담긴 현재 로그인 유저 PK
     */
    @DeleteMapping("/me")
    public Mono<ApiResponse<Void>> withdraw(@AuthenticationPrincipal Long userId) {
        // DB 삭제 + 소셜 연동 해제(Unlink) + Redis 세션 삭제를 수행
        return withdrawUseCase.withdraw(userId).map(success -> {
            if (success) return ApiResponse.noContent();
            return ApiResponse.fail(ErrorMessage.WITHDRAWAL_FAILED);
        });
    }

    /**
     * 로그아웃: 현재 사용 중인 토큰을 무효화합니다.
     */
    @PostMapping("/logout")
    public ApiResponse<Object> logout(HttpServletRequest request, HttpServletResponse response) {

        String accessToken = tokenExtractionUtils.extractAccessToken(request);
        String refreshToken = tokenExtractionUtils.extractRefreshToken(request);
        logoutUseCase.logout(accessToken, refreshToken);

        addCookieToResponse(response, "", 0);
        return ApiResponse.success(null);
    }

    /**
     * 전역 로그아웃: 모든 계정을 비활성화한다. 그리고 현재 사용 중인 토큰을 무효화한다.
     */
    @PostMapping("/logout/all")
    public ApiResponse<Void> globalLogout(HttpServletRequest request, HttpServletResponse response) {

        String accessToken = tokenExtractionUtils.extractAccessToken(request);
        logoutUseCase.globalLogout(accessToken);
        addCookieToResponse(response, "", 0);
        return ApiResponse.success(null);
    }

    /**
     * 토큰 재발급: 액세스 토큰이 만료되었을 때 리프레시 토큰을 통해 새 토큰 세트를 발급받습니다.
     * (Refresh Token Rotation 정책 적용)
     */
    @PostMapping("/reissue")
    public Mono<ApiResponse<TokenResponse>> reissue(HttpServletRequest request, HttpServletResponse response) {
        String accessToken = tokenExtractionUtils.extractAccessToken(request);
        String refreshToken = tokenExtractionUtils.extractRefreshToken(request);

        if (!StringUtils.hasText(refreshToken)) {
            // 파이프라인 내부 에러 처리를 위해 Mono.error 사용 권장
            return Mono.error(new BusinessException(ErrorMessage.JWT_TOKEN_IS_EMPTY));
        }

        return Mono.fromCallable(() -> reissueUseCase.reissue(accessToken, refreshToken))
                .subscribeOn(Schedulers.boundedElastic())
                .map(tokenResponse -> {
                    addCookieToResponse(response, tokenResponse.refreshTokenResponse().opaqueToken(), 60 * 24 * 60 * 60);
                    return ApiResponse.success(tokenResponse);
                });
    }

    private void addCookieToResponse(HttpServletResponse response, String token, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(refreshTokenCookie, token)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}