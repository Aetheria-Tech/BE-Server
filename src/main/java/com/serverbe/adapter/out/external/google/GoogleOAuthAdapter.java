package com.serverbe.adapter.out.external.google;

import com.serverbe.adapter.out.external.google.dto.GoogleTokenResponse;
import com.serverbe.adapter.out.external.google.dto.GoogleUserInfoResponse;
import com.serverbe.application.port.out.dto.oauth.OAuthUserInfoResult;
import com.serverbe.application.port.out.dto.oauth.SocialTokenRefreshResult;
import com.serverbe.application.port.out.oauth.OAuthClientPort;
import com.serverbe.domain.model.user.vo.OAuthProvider;
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

/**
 * @author Duskafka
 * @responsibility 구글 OAuth 서버와 협력하여 사용자 인증 관리를 하는 책임.
 * @see OAuthClientPort
 */
@Slf4j
@Component
public class GoogleOAuthAdapter implements OAuthClientPort {

    private final GoogleProperties googleProperties;

    private final String oauthUrl;
    private final String apiUrl;

    private final WebClient webClient;

    public GoogleOAuthAdapter(GoogleProperties googleProperties, WebClient.Builder webClientBuilder) {
        this.oauthUrl = googleProperties.auth().oauthApi();
        this.apiUrl = googleProperties.auth().api();
        this.googleProperties = googleProperties;
        this.webClient = webClientBuilder
                .build();
    }

    /**
     * 인가 코드로 해당 플랫폼의 토큰 및 유저 정보를 가져옵니다.
     *
     * @param code     OAuth 서버에서 받아온 인가 코드
     * @param provider 사용자가 요청한 OAuth 서버로 {@link OAuthProvider}를 받는다.
     * @return {@link OAuthUserInfoResult}로 사용자의 정보를 응답한다.
     * @responsibility 로그인이 성공한 사용자의 OAuth 코드로 사용자 정보를 받아온다.
     */
    @Override
    public Mono<OAuthUserInfoResult> getUserInfo(String code, OAuthProvider provider) {
        // 1. 토큰 교환 (액세스 토큰과 리프레시 토큰을 모두 받아옴)
        return getGoogleTokenResponse(code)
                .flatMap(response -> this.fetchUserInfo(response.accessToken())
                        .map(userInfo -> new OAuthUserInfoResult(
                                userInfo.sub(),
                                OAuthProvider.GOOGLE,
                                userInfo.email(),
                                userInfo.name(),
                                response.refreshToken() // 여기서 매번 받은 리프레시 토큰을 넘깁니다.
                        )));
    }

    /**
     * 소셜 리프레시 토큰을 사용하여 새로운 소셜 토큰 세트를 발급받습니다.
     *
     * @param code 사용자가 로그인 성공으로 받아온 인가 코드
     * @return {@link GoogleTokenResponse}로 구글에서 받아온 토큰 정보를 응답한다.
     * @responsibility 사용자 인가 코드로 토큰을 구글 OAuth 서버에서 받아오는 책임.
     * @implSpec 외부 서버와 통신하기 때문에 {@link WebClient}를 사용하며 리액티브 스트림을 응답한다.
     */
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
                .uri(oauthUrl + "/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .onStatus(HttpStatusCode::isError, res -> res.bodyToMono(String.class)
                        .map(body -> new BusinessException(ErrorMessage.FAILED_GOOGLE_API, "Google Token Error: " + body)))
                .bodyToMono(GoogleTokenResponse.class);
    }

    /**
     * @param accessToken 사용자가 구글 OAuth 로그인을 성공하고 받아온 액세스 토큰
     * @return {@link GoogleUserInfoResponse}로 사용자 정보를 응답한다.
     * @responsibility 액세스 토큰을 사용해서 구글 OAuth 서버에 사용자 정보 요청을 한다.
     * @implSpec 외부 서버와 통신하기 때문에 {@link WebClient}를 사용하며 리액티브 스트림으로 응답한다.
     */
    private Mono<GoogleUserInfoResponse> fetchUserInfo(String accessToken) {
        return webClient.get()
                .uri(apiUrl + "/oauth2/v3/userinfo")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(GoogleUserInfoResponse.class);
    }

    /**
     * 소셜 서비스와 우리 앱의 연동을 해제합니다.
     *
     * @param provider          사용자가 사용사는 통신 방법
     * @param oauthId           사용자의 OAuthID
     * @param oauthRefreshToken 사용자의 OAuth 리프레시 토큰 (Google은 리프레시 토큰을 사용한다)
     * @return {@code Boolean}으로 회원 탈퇴에 성공했다면 {@code True}, 실패했다면 {@code False}를 응답한다
     * @implSpec 외부 서버와 통신하기 때문에 리액티브 스트림으로 응답한다.
     * @responsibility 사용자 회원 탈퇴를 수행한다.
     */
    @Override
    public Mono<Boolean> unlink(OAuthProvider provider, String oauthId, String oauthRefreshToken) {
        if (oauthRefreshToken == null || oauthRefreshToken.isBlank()) {
            throw new BusinessException(ErrorMessage.INVALID_REFRESH_TOKEN, "구글 리프레시 토큰이 없어 연동 해제가 불가능합니다.");
        }

        return webClient.post()
                .uri(URI.create(apiUrl + "/revoke"))
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

    /**
     * 소셜 리프레시 토큰을 사용하여 새로운 소셜 토큰 세트를 발급받습니다.
     * @deprecated
     */
    @Override
    public Mono<SocialTokenRefreshResult> refreshSocialToken(OAuthProvider provider, String refreshToken) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "refresh_token");
        formData.add("client_id", googleProperties.auth().clientId());
        formData.add("client_secret", googleProperties.auth().clientSecret());
        formData.add("refresh_token", refreshToken);

        return webClient.post()
                .uri(oauthUrl + "/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .onStatus(HttpStatusCode::isError, res -> res.bodyToMono(String.class)
                        .map(body -> new BusinessException(ErrorMessage.FAILED_GOOGLE_API, "Google Refresh Error: " + body)))
                .bodyToMono(SocialTokenRefreshResult.class);
    }

    /**
     * 이 어댑터가 해당 provider를 지원하는지 확인
     *
     * @param provider 사용자가 사용하는 OAuth 서버
     * @return 이 어댑터를 사용해야 한다면 true, 아니라면 false
     * @responsibility 만약 {@link OAuthProvider}가 {@code GOOGLE}이라면 이 어댑터를 사용할 수 있도록 한다.
     */
    @Override
    public boolean supports(OAuthProvider provider) {
        return provider == OAuthProvider.GOOGLE;
    }

    /**
     * 소셜 플랫폼의 로그인 페이지 URL을 반환합니다.
     *
     * @return 로그인을 수행할 수 있는 URL
     * @responsibility 로그인을 수행할 수 있는 URL을 응답한다.
     */
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