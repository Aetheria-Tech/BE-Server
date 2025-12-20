package com.serverbe.adapter.in.web;

import com.serverbe.application.port.in.dto.AccessTokenResponse;
import com.serverbe.application.port.in.dto.TokenResponse;
import com.serverbe.application.port.in.oauth.LogoutUseCase;
import com.serverbe.application.port.in.oauth.SocialLoginUseCase;
import com.serverbe.application.port.in.oauth.WithdrawUseCase;
import com.serverbe.application.port.in.security.ReissueUseCase;
import com.serverbe.domain.model.vo.OAuthProvider;
import com.serverbe.infrastructure.common.ApiResponse;
import com.serverbe.infrastructure.config.properties.JwtProperties;
import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import com.serverbe.infrastructure.util.TokenExtractionUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.IOException;

/**
 * 인증 및 권한 관련 HTTP 요청을 처리하는 웹 어댑터입니다.
 * 소셜 로그인 시작, 콜백 처리, 토큰 재발급, 로그아웃, 회원 탈퇴를 담당합니다.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final SocialLoginUseCase socialLoginUseCase;
    private final WithdrawUseCase withdrawUseCase;
    private final LogoutUseCase logoutUseCase;
    private final ReissueUseCase reissueUseCase;
    private final TokenExtractionUtils tokenExtractionUtils;
    private final String REFRESH_TOKEN_COOKIE;

    public AuthController(
            SocialLoginUseCase socialLoginUseCase,
            WithdrawUseCase withdrawUseCase,
            LogoutUseCase logoutUseCase,
            ReissueUseCase reissueUseCase,
            TokenExtractionUtils tokenExtractionUtils,
            JwtProperties jwtProperties
    ) {
        this.socialLoginUseCase = socialLoginUseCase;
        this.withdrawUseCase = withdrawUseCase;
        this.logoutUseCase = logoutUseCase;
        this.reissueUseCase = reissueUseCase;
        this.tokenExtractionUtils = tokenExtractionUtils;
        this.REFRESH_TOKEN_COOKIE = jwtProperties.refreshToken().cookie();
    }


    /**
     * 소셜 로그인 시작: 사용자를 소셜 서비스(카카오/구글)의 로그인 페이지로 보냅니다.
     *
     * @param provider KAKAO 또는 GOOGLE
     * @param response 리다이렉션을 위한 응답 객체
     */
    @GetMapping("/login/{provider}")
    public void redirectToSocial(
            @PathVariable(value = "provider") OAuthProvider provider,
            HttpServletResponse response
    ) throws IOException {
        String redirectUrl = socialLoginUseCase.getSocialLoginUrl(provider);
        response.sendRedirect(redirectUrl);
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
        return socialLoginUseCase.login(code, provider)
                .doOnSuccess(tokenResponse -> {
                    ResponseCookie refreshTokenCookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, tokenResponse.refreshTokenResponse().opaqueToken())
                            .httpOnly(true)    // 자바스크립트 접근 차단 (XSS 방지)
                            .secure(true)      // HTTPS 환경에서만 전송
                            .path("/")         // 모든 경로에서 쿠키 유효
                            .maxAge(60 * 24 * 60 * 60) // 60일 (Duration을 초 단위로 변환)
                            .sameSite("Lax")   // CSRF 어느 정도 방지
                            .build();
                    response.addHeader("Set-Cookie", refreshTokenCookie.toString());
                })
                .map(tokenResponse -> ApiResponse.success(tokenResponse.accessTokenResponse()));
    }

    /**
     * 회원 탈퇴: 사용자의 계정을 삭제하고 소셜 서비스와의 연동을 끊습니다.
     *
     * @param userId JwtAuthenticationFilter에 의해 SecurityContext에 담긴 현재 로그인 유저 PK
     */
    @DeleteMapping("/me")
    public Mono<ApiResponse<Boolean>> withdraw(@AuthenticationPrincipal Long userId) {
        // DB 삭제 + 소셜 연동 해제(Unlink) + Redis 세션 삭제를 수행
        return withdrawUseCase.withdraw(userId).map(ApiResponse::success);
    }

    /**
     * 로그아웃: 현재 사용 중인 토큰을 무효화합니다.
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        // 헤더에서 토큰 추출 (resolveToken 메서드 활용)
        String accessToken = tokenExtractionUtils.extractAccessToken(request);
        String refreshToken = tokenExtractionUtils.extractRefreshToken(request);

        logoutUseCase.logout(accessToken, refreshToken);

        response.addHeader("Set-Cookie", revokeCookie().toString());

        return ApiResponse.success(null);
    }

    /**
     * 전역 로그아웃: 모든 계정을 비활성화한다. 그리고 현재 사용 중인 토큰을 무효화한다.
     */
    @PostMapping("/logout/all")
    public ApiResponse<Void> globalLogout(HttpServletRequest request, HttpServletResponse response) {
        logoutUseCase.globalLogout(tokenExtractionUtils.extractAccessToken(request));

        response.addHeader("Set-Cookie", revokeCookie().toString());

        return ApiResponse.success(null);
    }

    /**
     * 토큰 재발급: 액세스 토큰이 만료되었을 때 리프레시 토큰을 통해 새 토큰 세트를 발급받습니다.
     * (Refresh Token Rotation 정책 적용)
     */
    @PostMapping("/reissue")
    public ApiResponse<TokenResponse> reissue(HttpServletRequest request, HttpServletResponse response) {
        // 클라이언트로부터 전달받은 리프레시 토큰 추출
        String accessToken = tokenExtractionUtils.extractAccessToken(request);
        String refreshToken = tokenExtractionUtils.extractRefreshToken(request);

        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException(ErrorMessage.JWT_TOKEN_IS_EMPTY);
        }

        // 리프레시 토큰의 유효성과 Redis 존재 여부를 확인 후 토큰 세트(AT, RT) 재발급
        TokenResponse tokenResponse = reissueUseCase.reissue(accessToken, refreshToken);

        ResponseCookie refreshTokenCookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, tokenResponse.refreshTokenResponse().opaqueToken())
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(60 * 24 * 60 * 60) // 60일
                .sameSite("Lax")
                .build();
        response.addHeader("Set-Cookie", refreshTokenCookie.toString());
        return ApiResponse.success(tokenResponse);
    }

    /**
     * 무효화 쿠키 생성 메소드
     * */
    private ResponseCookie revokeCookie(){
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .path("/")
                .maxAge(0) // 즉시 만료
                .build();
    }
}