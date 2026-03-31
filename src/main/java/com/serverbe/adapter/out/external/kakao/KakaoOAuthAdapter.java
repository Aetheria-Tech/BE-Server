package com.serverbe.adapter.out.external.kakao;


import com.serverbe.adapter.out.external.kakao.dto.KakaoTokenResponse;
import com.serverbe.adapter.out.external.kakao.dto.KakaoUserInfoResponse;
import com.serverbe.application.port.out.dto.oauth.OAuthUserInfoResult;
import com.serverbe.application.port.out.dto.oauth.SocialTokenRefreshResult;
import com.serverbe.application.port.out.oauth.OAuthClientPort;
import com.serverbe.domain.exception.external.ExternalApiErrorCode;
import com.serverbe.domain.exception.external.ExternalApiException;
import com.serverbe.domain.exception.server.ServerErrorCode;
import com.serverbe.domain.exception.server.ServerException;
import com.serverbe.domain.model.user.vo.OAuthProvider;
import com.serverbe.infrastructure.config.properties.KakaoProperties;
import com.serverbe.domain.exception.BusinessException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
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

/**
 * @author Duskafka
 * @responsibility 카카오 OAuth 통신을 책임진다
 * @see OAuthClientPort
 */
@Slf4j
@Component
public class KakaoOAuthAdapter implements OAuthClientPort {

    private final WebClient webClient;
    private final String kauthUrl;
    private final String kapiUrl;
    private final String clientId;
    private final String adminKey;
    private final String redirectUri;

    public KakaoOAuthAdapter(KakaoProperties kakaoProperties, WebClient.Builder webClientBuilder) {
        this.kauthUrl = kakaoProperties.auth().kauth();
        this.kapiUrl = kakaoProperties.auth().kapi();
        this.clientId = kakaoProperties.clientId();
        this.adminKey = kakaoProperties.adminKey();
        this.redirectUri = kakaoProperties.auth().redirectUri();

        // 주입받은 Builder를 사용하여 카카오 API 전용 설정을 입힌 WebClient 생성
        this.webClient = webClientBuilder
                .build();
    }

    /**
     * 인가 코드로 해당 플랫폼의 토큰 및 유저 정보를 가져옵니다.
     *
     * @param code     OAuth 서버에서 받아온 인가 코드
     * @param provider 사용자가 요청한 OAuth 서버로 {@link OAuthProvider}를 받는다.
     * @return {@link OAuthUserInfoResult}로 사용자의 정보를 응답한다.
     * @throws BusinessException provider 매개변수가 KAKAO가 아닐 때 예외를 발생시킨다.
     * @responsibility 로그인이 성공한 사용자의 OAuth 코드로 사용자 정보를 받아온다.
     */
    @Override
    @CircuitBreaker(name = "kakaoApi", fallbackMethod = "fallbackGetUserInfo")
    public Mono<OAuthUserInfoResult> getUserInfo(String code, OAuthProvider provider) {
        if (provider != OAuthProvider.KAKAO) {
            return Mono.error(new ServerException(
                    ServerErrorCode.INTERNAL_SERVER_ERROR,
                    "카카오 어댑터는 카카오 로그인만 처리할 수 있습니다."
            ));
        }

        // 1. 인가 코드로 카카오 액세스/리프레시 토큰 받기
        return getKakaoAccessToken(code)
                .flatMap(accessToken -> fetchUserInfo(
                                accessToken.accessToken(),
                                accessToken.refreshToken()
                        )
                );
    }

    public Mono<OAuthUserInfoResult> fallbackGetUserInfo(String code, OAuthProvider provider, Throwable t) {
        log.error("🚨 [CircuitBreaker/Timeout] 카카오 로그인 API 장애 발생: {}", t.getMessage());
        return Mono.error(new ExternalApiException(
                ExternalApiErrorCode.FAILED_SOCIAL_API,
                "현재 카카오 로그인 서버의 응답이 지연되고 있습니다. 잠시 후 다시 시도해주세요."
        ));
    }

    /**
     * @param code 카카오 OAuth 서버에서 받아온 인가 코드
     * @return {@link KakaoTokenResponse}로 토큰을 응답한다.
     * @responsibility 카카오 OAuth에서 받아온 인가 코드로 토큰을 발급 받는다.
     * @implSpec 외부 서버와 통신하기 때문에 {@link WebClient}를 사용하며 리액티브 스트림으로 응답한다.
     */
    private Mono<KakaoTokenResponse> getKakaoAccessToken(String code) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("client_id", clientId);
        formData.add("redirect_uri", redirectUri);
        formData.add("code", code);

        return webClient.post()
                .uri(kauthUrl + "/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .map(body -> new ExternalApiException(ExternalApiErrorCode.FAILED_SOCIAL_API, body)))
                .bodyToMono(KakaoTokenResponse.class);
    }

    /**
     * @param accessToken  유저 정보를 받아오는데 필요한 액세스 토큰
     * @param refreshToken 유저 정보를 받아오는데 필요한 리프레시 토큰
     * @implSpec 외부 서버와 통신하기 때문에 {@link WebClient}를 사용하며 리액티브 스트림으로 응답한다.
     * @responsibility 액세스 토큰을 사용해서 카카오 OAuth 서버에 사용자 정보 요청을 한다.
     */
    private Mono<OAuthUserInfoResult> fetchUserInfo(String accessToken, String refreshToken) {
        return webClient.get()
                .uri(kapiUrl + "/v2/user/me")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .map(body -> new ExternalApiException(ExternalApiErrorCode.FAILED_SOCIAL_API, body)))
                .bodyToMono(KakaoUserInfoResponse.class)
                .map(response -> new OAuthUserInfoResult(
                        String.valueOf(response.id()),
                        OAuthProvider.KAKAO,
                        response.kakaoAccount().email(),
                        response.kakaoAccount().profile().nickname(),
                        refreshToken
                ));
    }

    /**
     * 소셜 서비스와 우리 앱의 연동을 해제합니다.
     *
     * @param provider          사용자가 사용하는 통신 방법
     * @param oauthId           사용자의 OAuthID
     * @param oauthRefreshToken 사용자의 OAuth 리프레시 토큰 (Google은 리프레시 토큰을 사용한다)
     * @return {@code Boolean}으로 회원 탈퇴에 성공했다면 {@code True}, 실패했다면 {@code False}를 응답한다
     * @implSpec 외부 서버와 통신하기 때문에 리액티브 스트림으로 응답한다.
     * @responsibility 사용자 회원 탈퇴를 수행한다.
     */
    @Override
    @CircuitBreaker(name = "kakaoApi", fallbackMethod = "fallbackUnlink")
    public Mono<Boolean> unlink(OAuthProvider provider, String oauthId, String oauthRefreshToken) {
        // 카카오 어드민 키 방식 (사용자 동의 없이도 서버에서 강제 해제 가능)
        return webClient.post()
                .uri(kapiUrl + "/v1/user/unlink") // API 도메인 확인 (kapi.kakao.com)
                .header("Authorization", "KakaoAK " + adminKey)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("target_id_type", "user_id")
                        .with("target_id", oauthId)) // oauthId는 숫자(Long) 형태의 카카오 회원번호여야 함
                .retrieve()
                .onStatus(HttpStatusCode::isError, res -> res.bodyToMono(String.class)
                        .map(body -> new ExternalApiException(ExternalApiErrorCode.FAILED_SOCIAL_API, "Kakao Unlink Failed: " + body)))
                .toBodilessEntity()
                .map(response -> true)
                // 에러 발생 시(BusinessException 포함) 흐름을 끊지 않고 false로 치환하고 싶다면 아래 주석 활용
                // .onErrorReturn(false)
                .defaultIfEmpty(false);
    }

    public Mono<Boolean> fallbackUnlink(OAuthProvider provider, String oauthId, String oauthRefreshToken, Throwable t) {
        log.error("🚨 [CircuitBreaker/Timeout] 카카오 연동 해제 API 장애 발생: {}", t.getMessage());
        // 연동 해제 실패 시 false를 반환하여 호출 측에서 예외 처리를 하거나, 또는 아래처럼 에러를 던질 수 있습니다.
        return Mono.error(new ExternalApiException(
                ExternalApiErrorCode.FAILED_SOCIAL_API,
                "카카오 서버 지연으로 인해 연동 해제에 실패했습니다."
        ));
    }

    /**
     * 소셜 리프레시 토큰을 사용하여 새로운 소셜 토큰 세트를 발급받습니다.
     *
     * @deprecated
     */
    @Override
    @CircuitBreaker(name = "kakaoApi", fallbackMethod = "fallbackRefreshSocialToken")
    public Mono<SocialTokenRefreshResult> refreshSocialToken(OAuthProvider provider, String refreshToken) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "refresh_token");
        formData.add("client_id", clientId);
        formData.add("refresh_token", refreshToken);

        return webClient.post()
                .uri(kauthUrl + "/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .onStatus(HttpStatusCode::isError, res -> res.bodyToMono(String.class)
                        .map(body -> new ExternalApiException(ExternalApiErrorCode.FAILED_SOCIAL_API, "Kakao Refresh Error: " + body)))
                .bodyToMono(SocialTokenRefreshResult.class);
    }

    public Mono<SocialTokenRefreshResult> fallbackRefreshSocialToken(OAuthProvider provider, String refreshToken, Throwable t) {
        log.error("🚨 [CircuitBreaker/Timeout] 카카오 토큰 갱신 API 장애 발생: {}", t.getMessage());
        return Mono.error(new ExternalApiException(
                ExternalApiErrorCode.FAILED_SOCIAL_API,
                "카카오 서버 지연으로 인해 토큰 갱신에 실패했습니다."
        ));
    }

    /**
     * 이 어댑터가 해당 provider를 지원하는지 확인
     *
     * @param provider 사용자가 사용하는 OAuth 서버
     * @return 이 어댑터를 사용해야 한다면 true, 아니라면 false
     * @responsibility 만약 {@link OAuthProvider}가 {@code KAKAO}이라면 이 어댑터를 사용할 수 있도록 한다.
     */
    @Override
    public boolean supports(OAuthProvider provider) {
        return provider == OAuthProvider.KAKAO;
    }

    /**
     * 소셜 플랫폼의 로그인 페이지 URL을 반환합니다.
     *
     * @return 로그인을 수행할 수 있는 URL
     * @responsibility 로그인을 수행할 수 있는 URL을 응답한다.
     */
    @Override
    public String getLoginUrl() {
        return UriComponentsBuilder.fromHttpUrl(kauthUrl + "/oauth/authorize")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                // .queryParam("scope", "account_email,profile_nickname") // 필요 시 동의 항목 지정
                // .queryParam("prompt", "login") // 매번 카카오 계정 로그인을 요구할 경우 추가
                .build()
                .toUriString();
    }
}