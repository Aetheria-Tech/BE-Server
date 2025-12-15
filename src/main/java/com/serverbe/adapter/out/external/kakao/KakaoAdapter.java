package com.serverbe.adapter.out.external.kakao;


import com.serverbe.adapter.out.external.kakao.dto.KakaoTokenResponse;
import com.serverbe.adapter.out.external.kakao.dto.KakaoUserInfoResponse;
import com.serverbe.application.port.in.dto.OAuthUserInfo;
import com.serverbe.application.port.in.oauth.OAuthClientPort;
import com.serverbe.domain.model.vo.OAuthProvider;
import com.serverbe.infrastructure.config.properties.KakaoProperties;
import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class KakaoAdapter implements OAuthClientPort {

    private final KakaoProperties kakaoProperties;
    private final WebClient webClient;

    public KakaoAdapter(KakaoProperties kakaoProperties, WebClient.Builder webClientBuilder) {
        this.kakaoProperties = kakaoProperties;

        // 주입받은 Builder를 사용하여 카카오 API 전용 설정을 입힌 WebClient 생성
        this.webClient = webClientBuilder
                .baseUrl(kakaoProperties.auth().api()) // 기본 API 경로 설정
                .build();
    }

    @Override
    public OAuthUserInfo getUserInfo(String code, OAuthProvider provider) {
        if (provider != OAuthProvider.KAKAO) {
            throw new BusinessException(ErrorMessage.INTERNAL_SERVER_ERROR, "카카오 어댑터는 카카오 로그인만 처리할 수 있습니다.");
        }

        // 1. 인가 코드로 카카오 액세스/리프레시 토큰 받기
        String accessToken = getKakaoAccessToken(code);

        // 2. 액세스 토큰으로 카카오 유저 정보 받기
        return fetchUserInfo(accessToken);
    }

    private String getKakaoAccessToken(String code) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("client_id", kakaoProperties.auth().clientId());
        formData.add("redirect_uri", kakaoProperties.auth().redirectUri());
        formData.add("code", code);

        KakaoTokenResponse response = webClient.post()
                .uri(kakaoProperties.auth().authApi() + "/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .map(body -> new BusinessException(ErrorMessage.FAILED_KAKAO_API, body)))
                .bodyToMono(KakaoTokenResponse.class)
                .block(); // 유스케이스 흐름을 위해 동기 방식으로 처리

        return response.accessToken();
    }

    private OAuthUserInfo fetchUserInfo(String accessToken) {
        KakaoUserInfoResponse response = webClient.get()
                .uri(kakaoProperties.auth().api() + "/v2/user/me")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .map(body -> new BusinessException(ErrorMessage.FAILED_KAKAO_API, body)))
                .bodyToMono(KakaoUserInfoResponse.class)
                .block();

        return new OAuthUserInfo(
                String.valueOf(response.id()),
                OAuthProvider.KAKAO,
                response.kakaoAccount().email(),
                response.kakaoAccount().profile().nickname(),
                null // 카카오 리프레시 토큰이 필요하다면 getKakaoAccessToken에서 받아와 전달
        );
    }
}