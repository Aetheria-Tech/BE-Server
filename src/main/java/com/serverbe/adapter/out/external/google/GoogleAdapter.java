package com.serverbe.adapter.out.external.google;

import com.serverbe.adapter.out.external.google.dto.GoogleTokenResponse;
import com.serverbe.adapter.out.external.google.dto.GoogleUserInfoResponse;
import com.serverbe.application.port.in.dto.OAuthUserInfo;
import com.serverbe.application.port.in.oauth.OAuthClientPort;
import com.serverbe.domain.model.vo.OAuthProvider;
import com.serverbe.infrastructure.config.properties.GoogleProperties;
import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
public class GoogleAdapter implements OAuthClientPort {

    private final GoogleProperties googleProperties;
    private final WebClient webClient;

    public GoogleAdapter(GoogleProperties googleProperties, WebClient.Builder webClientBuilder) {
        this.googleProperties = googleProperties;
        this.webClient = webClientBuilder
                .baseUrl(googleProperties.auth().api())
                .build();
    }

    @Override
    public OAuthUserInfo getUserInfo(String code, OAuthProvider provider) {
        // 1. 토큰 교환 (액세스 토큰과 리프레시 토큰을 모두 받아옴)
        GoogleTokenResponse tokenResponse = getGoogleTokenResponse(code);

        // 2. 액세스 토큰으로 유저 정보 조회
        GoogleUserInfoResponse userInfo = fetchUserInfo(tokenResponse.accessToken());

        // 3. 응답 객체 생성 (받아온 refresh_token을 포함)
        return new OAuthUserInfo(
                userInfo.sub(),
                OAuthProvider.GOOGLE,
                userInfo.email(),
                userInfo.name(),
                tokenResponse.refreshToken() // 여기서 매번 받은 리프레시 토큰을 넘깁니다.
        );
    }

    public String getGoogleRedirectUrl() {
        return UriComponentsBuilder.fromHttpUrl("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", googleProperties.auth().clientId())
                .queryParam("redirect_uri", googleProperties.auth().redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "email profile")
                .queryParam("access_type", "offline") // 리프레시 토큰 발급을 위해 필수
                .queryParam("prompt", "consent")      // 매번 동의창을 띄워 새 리프레시 토큰 강제
                .build()
                .toUriString();
    }

    private GoogleTokenResponse getGoogleTokenResponse(String code) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("code", code);
        formData.add("client_id", googleProperties.auth().clientId());
        formData.add("client_secret", googleProperties.auth().clientSecret());
        formData.add("redirect_uri", googleProperties.auth().redirectUri());
        formData.add("grant_type", "authorization_code");

        // 주의: 구글 리프레시 토큰을 매번 받으려면,
        // 이 '인가 코드'를 생성한 최초의 Redirect URL에 아래 파라미터가 포함되어 있어야 합니다:
        // access_type=offline
        // prompt=consent

        return webClient.post()
                .uri(googleProperties.auth().authApi() + "/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .onStatus(HttpStatusCode::isError, res -> res.bodyToMono(String.class)
                        .map(body -> new BusinessException(ErrorMessage.FAILED_GOOGLE_API, "Google Token Error: " + body)))
                .bodyToMono(GoogleTokenResponse.class)
                .block();
    }

    private GoogleUserInfoResponse fetchUserInfo(String accessToken) {
        return webClient.get()
                .uri("/oauth2/v3/userinfo")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(GoogleUserInfoResponse.class)
                .block();
    }

    @Override
    public void unlink(OAuthProvider provider, String oauthId, String oauthRefreshToken) {
        if (oauthRefreshToken == null || oauthRefreshToken.isBlank()) {
            throw new BusinessException(ErrorMessage.INVALID_REFRESH_TOKEN, "구글 리프레시 토큰이 없어 연동 해제가 불가능합니다.");
        }

        webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("https://oauth2.googleapis.com/revoke")
                        .queryParam("token", oauthRefreshToken)
                        .build())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .retrieve()
                .onStatus(HttpStatusCode::isError, res -> res.bodyToMono(String.class)
                        .map(body -> {
                            log.error("[Google Revoke Error] -> {}", body);
                            return new BusinessException(ErrorMessage.FAILED_KAKAO_API, "Google Revoke Failed");
                        }))
                .bodyToMono(Void.class)
                .block(); // 탈퇴 로직의 정합성을 위해 동기 처리
    }
}