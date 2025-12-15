package com.serverbe.adapter.in.web;

import com.serverbe.adapter.out.external.google.GoogleAdapter;
import com.serverbe.adapter.out.external.kakao.KakaoAdapter;
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
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.WebUtils;

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
    private final GoogleAdapter googleAdapter;
    private final KakaoAdapter kakaoAdapter;
    private final ReissueUseCase reissueUseCase;
    private final String ACCESS_TOKEN_HEADER;
    private final String REFRESH_TOKEN_COOKIE;

    public AuthController(
            SocialLoginUseCase socialLoginUseCase,
            WithdrawUseCase withdrawUseCase,
            LogoutUseCase logoutUseCase,
            GoogleAdapter googleAdapter,
            KakaoAdapter kakaoAdapter,
            ReissueUseCase reissueUseCase,
            JwtProperties jwtProperties
    ) {
        this.socialLoginUseCase = socialLoginUseCase;
        this.withdrawUseCase = withdrawUseCase;
        this.logoutUseCase = logoutUseCase;
        this.googleAdapter = googleAdapter;
        this.kakaoAdapter = kakaoAdapter;
        this.reissueUseCase = reissueUseCase;
        this.ACCESS_TOKEN_HEADER = jwtProperties.accessToken().header();
        this.REFRESH_TOKEN_COOKIE = jwtProperties.refreshToken().cookie();
    }


    /**
     * 소셜 로그인 시작: 사용자를 소셜 서비스(카카오/구글)의 로그인 페이지로 보냅니다.
     *
     * @param provider KAKAO 또는 GOOGLE
     * @param response 리다이렉션을 위한 응답 객체
     */
    @GetMapping("/login/{provider}")
    public void redirectToSocial(@PathVariable OAuthProvider provider, HttpServletResponse response) throws IOException {
        String redirectUrl = switch (provider) {
            case KAKAO -> kakaoAdapter.getKakaoRedirectUrl();
            case GOOGLE -> googleAdapter.getGoogleRedirectUrl();
        };
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
    public ApiResponse<TokenResponse> loginCallback(
            @PathVariable OAuthProvider provider,
            @RequestParam("code") String code,
            HttpServletResponse response
    ) {
        // 1. 인가 코드로 소셜 서버와 통신하여 유저 정보 획득
        // 2. 신규 유저면 가입, 기존 유저면 정보 업데이트(Upsert)
        // 3. 우리 서비스 전용 액세스/리프레시 토큰 발급 및 리프레시 토큰 Redis 저장
        TokenResponse tokenResponse = socialLoginUseCase.login(code, provider);

        // 리프레시 토큰을 쿠키로 생성
        ResponseCookie refreshTokenCookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, tokenResponse.refreshTokenIssueResult().opaqueToken())
                .httpOnly(true)    // 자바스크립트 접근 차단 (XSS 방지)
                .secure(true)      // HTTPS 환경에서만 전송
                .path("/")         // 모든 경로에서 쿠키 유효
                .maxAge(60 * 24 * 60 * 60) // 60일 (Duration을 초 단위로 변환)
                .sameSite("Lax")   // CSRF 어느 정도 방지
                .build();

        response.addHeader("Set-Cookie", refreshTokenCookie.toString());

        return ApiResponse.success(tokenResponse);
    }

    /**
     * 회원 탈퇴: 사용자의 계정을 삭제하고 소셜 서비스와의 연동을 끊습니다.
     *
     * @param userId JwtAuthenticationFilter에 의해 SecurityContext에 담긴 현재 로그인 유저 PK
     */
    @DeleteMapping("/me")
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal Long userId) {
        // DB 삭제 + 소셜 연동 해제(Unlink) + Redis 세션 삭제를 수행
        withdrawUseCase.withdraw(userId);
        return ApiResponse.success(null);
    }

    /**
     * 로그아웃: 현재 사용 중인 토큰을 무효화합니다.
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        // 헤더에서 토큰 추출 (resolveToken 메서드 활용)
        String accessToken = resolveToken(request);
        logoutUseCase.logout(accessToken);

        // 쿠키 무효화 (Max-Age를 0으로 설정)
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .path("/")
                .maxAge(0) // 즉시 만료
                .build();

        response.addHeader("Set-Cookie", cookie.toString());

        return ApiResponse.success(null);
    }

    /**
     * 토큰 재발급: 액세스 토큰이 만료되었을 때 리프레시 토큰을 통해 새 토큰 세트를 발급받습니다.
     * (Refresh Token Rotation 정책 적용)
     */
    @PostMapping("/reissue")
    public ApiResponse<TokenResponse> reissue(HttpServletRequest request) {
        // 클라이언트로부터 전달받은 리프레시 토큰 추출
        String refreshToken = resolveRefreshToken(request);

        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException(ErrorMessage.JWT_TOKEN_IS_EMPTY);
        }

        // 리프레시 토큰의 유효성과 Redis 존재 여부를 확인 후 토큰 세트(AT, RT) 재발급
        TokenResponse response = reissueUseCase.reissue(refreshToken);
        return ApiResponse.success(response);
    }

    /**
     * 유틸리티 메서드: Authorization 헤더에서 'Bearer ' 접두사를 제거하고 순수 토큰 값만 추출합니다.
     */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(ACCESS_TOKEN_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * 설정된 헤더(쿠키) 이름으로 리프레시 토큰을 추출합니다.
     */
    private String resolveRefreshToken(HttpServletRequest request) {
        // Spring의 WebUtils를 사용하면 쿠키 찾기가 매우 쉽습니다.
        Cookie cookie = WebUtils.getCookie(request, REFRESH_TOKEN_COOKIE);
        return (cookie != null) ? cookie.getValue() : null;
    }
}