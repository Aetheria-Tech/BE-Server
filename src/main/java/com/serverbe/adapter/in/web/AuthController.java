package com.serverbe.adapter.in.web;

import com.serverbe.adapter.out.external.google.GoogleAdapter;
import com.serverbe.adapter.out.external.kakao.KakaoAdapter;
import com.serverbe.application.port.in.dto.TokenResponse;
import com.serverbe.application.port.in.oauth.SocialLoginUseCase;
import com.serverbe.application.port.in.oauth.WithdrawUseCase;
import com.serverbe.domain.model.vo.OAuthProvider;
import com.serverbe.infrastructure.common.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SocialLoginUseCase socialLoginUseCase;
    private final WithdrawUseCase withdrawUseCase;
    private final GoogleAdapter googleAdapter;
    private final KakaoAdapter kakaoAdapter;

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

}