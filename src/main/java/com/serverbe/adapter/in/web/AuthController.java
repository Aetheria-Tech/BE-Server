package com.serverbe.adapter.in.web;

import com.serverbe.adapter.out.external.google.GoogleAdapter;
import com.serverbe.adapter.out.external.kakao.KakaoAdapter;
import com.serverbe.application.port.in.dto.TokenResponse;
import com.serverbe.application.port.in.oauth.LogoutUseCase;
import com.serverbe.application.port.in.oauth.SocialLoginUseCase;
import com.serverbe.application.port.in.oauth.WithdrawUseCase;
import com.serverbe.application.port.in.security.ReissueUseCase;
import com.serverbe.application.port.in.security.TokenResolver;
import com.serverbe.domain.model.vo.OAuthProvider;
import com.serverbe.infrastructure.common.ApiResponse;
import com.serverbe.infrastructure.config.properties.JwtProperties;
import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

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
        ACCESS_TOKEN_HEADER = jwtProperties.accessToken().header();
    }


    /**
     * 소셜 로그인 페이지로 리다이렉트
     * 사용자가 브라우저에서 이 엔드포인트를 호출합니다.
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
     * 소셜 로그인 콜백 처리
     * 카카오/구글 인증 완료 후, 브라우저가 이 엔드포인트로 'code'를 가지고 돌아옵니다.
     * 설정파일(yml)의 redirect-uri가 이 주소를 가리켜야 합니다.
     */
    @GetMapping("/callback/{provider}")
    public ApiResponse<TokenResponse> loginCallback(
            @PathVariable OAuthProvider provider,
            @RequestParam("code") String code
    ) {
        // 유스케이스를 호출하여 회원가입/로그인 및 토큰 발급 진행
        TokenResponse tokenResponse = socialLoginUseCase.login(code, provider);

        return ApiResponse.success(tokenResponse);
    }


    @DeleteMapping("/me")
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal Long userId) {
        withdrawUseCase.withdraw(userId);
        return ApiResponse.success(null);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        // 헤더에서 토큰 추출 (resolveToken 메서드 활용)
        String accessToken = resolveToken(request);
        logoutUseCase.logout(accessToken);
        return ApiResponse.success(null);
    }

    @PostMapping("/reissue")
    public ApiResponse<TokenResponse> reissue(HttpServletRequest request) {
        // 보통 리프레시 토큰은 전용 헤더나 쿠키에서 추출합니다.
        String refreshToken = request.getHeader("X-Refresh-Token");

        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException(ErrorMessage.JWT_TOKEN_IS_EMPTY);
        }

        TokenResponse response = reissueUseCase.reissue(refreshToken);
        return ApiResponse.success(response);
    }

    // 필터에 있던 로직을 가져옵니다.
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(ACCESS_TOKEN_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}