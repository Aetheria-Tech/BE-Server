package com.serverbe.adapter.out.external.kakao;


import com.serverbe.adapter.out.external.kakao.dto.KakaoTokenResponse;
import com.serverbe.adapter.out.external.kakao.dto.KakaoUserInfoResponse;
import com.serverbe.application.port.in.dto.OAuthUserInfo;
import com.serverbe.application.port.in.dto.SocialTokenRefreshResponse;
import com.serverbe.application.port.in.oauth.OAuthClientPort;
import com.serverbe.domain.model.vo.OAuthProvider;
import com.serverbe.infrastructure.config.properties.KakaoProperties;
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

@Slf4j
@Component
public class KakaoAdapter implements OAuthClientPort {

    private final WebClient webClient;
    private final String KAUTH_URL;
    private final String KAPI_URL;
    private final String CLIENT_ID;
    private final String ADMIN_KEY;
    private final String REDIRECT_URI;

    public KakaoAdapter(KakaoProperties kakaoProperties, WebClient.Builder webClientBuilder) {
        this.KAUTH_URL = kakaoProperties.auth().kauth();
        this.KAPI_URL = kakaoProperties.auth().kapi();
        this.CLIENT_ID = kakaoProperties.auth().clientId();
        this.ADMIN_KEY = kakaoProperties.adminKey();
        this.REDIRECT_URI = kakaoProperties.auth().redirectUri();

        // 주입받은 Builder를 사용하여 카카오 API 전용 설정을 입힌 WebClient 생성
        this.webClient = webClientBuilder
                .build();
    }


    @Override
    public Mono<OAuthUserInfo> getUserInfo(String code, OAuthProvider provider) {
        if (provider != OAuthProvider.KAKAO) {
            return Mono.error(new BusinessException(ErrorMessage.INTERNAL_SERVER_ERROR, "카카오 어댑터는 카카오 로그인만 처리할 수 있습니다."));
        }

        // 1. 인가 코드로 카카오 액세스/리프레시 토큰 받기
        return getKakaoAccessToken(code)
                .flatMap(accessToken -> fetchUserInfo(accessToken.accessToken(), accessToken.refreshToken()));
    }

    private Mono<KakaoTokenResponse> getKakaoAccessToken(String code) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("client_id", CLIENT_ID);
        formData.add("redirect_uri", REDIRECT_URI);
        formData.add("code", code);

        return webClient.post()
                .uri(KAUTH_URL + "/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .map(body -> new BusinessException(ErrorMessage.FAILED_KAKAO_API, body)))
                .bodyToMono(KakaoTokenResponse.class);
    }

    private Mono<OAuthUserInfo> fetchUserInfo(String accessToken, String refreshToken) {
        return webClient.get()
                .uri(KAPI_URL + "/v2/user/me")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .map(body -> new BusinessException(ErrorMessage.FAILED_KAKAO_API, body)))
                .bodyToMono(KakaoUserInfoResponse.class)
                .map(response -> new OAuthUserInfo(
                        String.valueOf(response.id()),
                        OAuthProvider.KAKAO,
                        response.kakaoAccount().email(),
                        response.kakaoAccount().profile().nickname(),
                        refreshToken
                ));
    }

    @Override
    public Mono<Boolean> unlink(OAuthProvider provider, String oauthId, String oauthRefreshToken) {
        // 카카오 어드민 키 방식 (사용자 동의 없이도 서버에서 강제 해제 가능)
        return webClient.post()
                .uri(KAPI_URL + "/v1/user/unlink") // API 도메인 확인 (kapi.kakao.com)
                .header("Authorization", "KakaoAK " + ADMIN_KEY)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("target_id_type", "user_id")
                        .with("target_id", oauthId)) // oauthId는 숫자(Long) 형태의 카카오 회원번호여야 함
                .retrieve()
                .onStatus(HttpStatusCode::isError, res -> res.bodyToMono(String.class)
                        .map(body -> new BusinessException(ErrorMessage.FAILED_KAKAO_API, "Kakao Unlink Failed: " + body)))
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
        formData.add("client_id", CLIENT_ID);
        formData.add("refresh_token", refreshToken);

        return webClient.post()
                .uri(KAUTH_URL + "/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .onStatus(HttpStatusCode::isError, res -> res.bodyToMono(String.class)
                        .map(body -> new BusinessException(ErrorMessage.FAILED_KAKAO_API, "Kakao Refresh Error: " + body)))
                .bodyToMono(SocialTokenRefreshResponse.class);
    }

    @Override
    public boolean supports(OAuthProvider provider) {
        return provider == OAuthProvider.KAKAO;
    }

    @Override
    public String getLoginUrl() {
        return UriComponentsBuilder.fromHttpUrl(KAUTH_URL + "/oauth/authorize")
                .queryParam("client_id", CLIENT_ID)
                .queryParam("redirect_uri", REDIRECT_URI)
                .queryParam("response_type", "code")
                // .queryParam("scope", "account_email,profile_nickname") // 필요 시 동의 항목 지정
                // .queryParam("prompt", "login") // 매번 카카오 계정 로그인을 요구할 경우 추가
                .build()
                .toUriString();
    }
}