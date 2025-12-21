package com.serverbe.adapter.out.external.google;

import com.serverbe.adapter.out.external.google.dto.GoogleTokenResponse;
import com.serverbe.adapter.out.external.google.dto.GoogleUserInfoResponse;
import com.serverbe.application.port.out.dto.oauth.OAuthUserInfo;
import com.serverbe.application.port.out.dto.oauth.SocialTokenRefreshResponse;
import com.serverbe.application.port.out.oauth.OAuthClientPort;
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
import reactor.core.publisher.Mono;

import java.net.URI;

@Slf4j
@Component
public class GoogleAdapter implements OAuthClientPort {

    private final GoogleProperties googleProperties;

    private final String OAUTH_URL;
    private final String API_URL;


    private final WebClient webClient;

    public GoogleAdapter(GoogleProperties googleProperties, WebClient.Builder webClientBuilder) {
        this.OAUTH_URL = googleProperties.auth().oauthApi();
        this.API_URL = googleProperties.auth().api();
        this.googleProperties = googleProperties;
        this.webClient = webClientBuilder
                .build();
    }

    @Override
    public Mono<OAuthUserInfo> getUserInfo(String code, OAuthProvider provider) {
        // 1. 토큰 교환 (액세스 토큰과 리프레시 토큰을 모두 받아옴)
        return getGoogleTokenResponse(code)
                .flatMap(response -> this.fetchUserInfo(response.accessToken())
                        .map(userInfo -> new OAuthUserInfo(
                                userInfo.sub(),
                                OAuthProvider.GOOGLE,
                                userInfo.email(),
                                userInfo.name(),
                                response.refreshToken() // 여기서 매번 받은 리프레시 토큰을 넘깁니다.
                        )));
    }

    private Mono<GoogleTokenResponse> getGoogleTokenResponse(String code) {
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
                .uri(OAUTH_URL + "/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .onStatus(HttpStatusCode::isError, res -> res.bodyToMono(String.class)
                        .map(body -> new BusinessException(ErrorMessage.FAILED_GOOGLE_API, "Google Token Error: " + body)))
                .bodyToMono(GoogleTokenResponse.class);
    }

    private Mono<GoogleUserInfoResponse> fetchUserInfo(String accessToken) {
        return webClient.get()
                .uri(API_URL + "/oauth2/v3/userinfo")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(GoogleUserInfoResponse.class);
    }

    @Override
    public Mono<Boolean> unlink(OAuthProvider provider, String oauthId, String oauthRefreshToken) {
        if (oauthRefreshToken == null || oauthRefreshToken.isBlank()) {
            throw new BusinessException(ErrorMessage.INVALID_REFRESH_TOKEN, "구글 리프레시 토큰이 없어 연동 해제가 불가능합니다.");
        }

        return webClient.post()
                .uri(URI.create(API_URL + "/revoke"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("token", oauthRefreshToken)) // 리프레시 토큰 전송
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .flatMap(error -> Mono.error(new BusinessException(ErrorMessage.FAILED_GOOGLE_API, error))))
                .toBodilessEntity()
                .map(response -> true)
                // 에러 발생 시(BusinessException 포함) 흐름을 끊지 않고 false로 치환하고 싶다면 아래 주석 활용
                // .onErrorReturn(false)
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<SocialTokenRefreshResponse> refreshSocialToken(OAuthProvider provider, String refreshToken) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "refresh_token");
        formData.add("client_id", googleProperties.auth().clientId());
        formData.add("client_secret", googleProperties.auth().clientSecret());
        formData.add("refresh_token", refreshToken);

        return webClient.post()
                .uri(OAUTH_URL + "/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .onStatus(HttpStatusCode::isError, res -> res.bodyToMono(String.class)
                        .map(body -> new BusinessException(ErrorMessage.FAILED_GOOGLE_API, "Google Refresh Error: " + body)))
                .bodyToMono(SocialTokenRefreshResponse.class);
    }

    @Override
    public boolean supports(OAuthProvider provider) {
        return provider == OAuthProvider.GOOGLE;
    }

    @Override
    public String getLoginUrl() {
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
}