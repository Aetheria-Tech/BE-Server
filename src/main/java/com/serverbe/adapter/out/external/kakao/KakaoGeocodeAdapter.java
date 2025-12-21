package com.serverbe.adapter.out.external.kakao;

import com.serverbe.adapter.out.external.kakao.dto.KakaoGeocodeResponse;
import com.serverbe.application.port.out.dto.geocoding.GeocodeResponse;
import com.serverbe.application.port.out.geocode.GeocodePort;
import com.serverbe.infrastructure.config.properties.KakaoProperties;
import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 카카오 로컬 API를 사용하여 지오코딩을 수행하는 어댑터입니다.
 */
@Slf4j
@Component
public class KakaoGeocodeAdapter implements GeocodePort {

    private final WebClient webClient;
    private final String clientId;
    private final String geocodeApiUrl;

    public KakaoGeocodeAdapter(
            WebClient.Builder builder,
            KakaoProperties kakaoProperties
    ) {
        // baseUrl은 호스트까지만 설정하는 것이 관례이며, 나머지는 uri()에서 처리합니다.
        this.webClient = builder.baseUrl(kakaoProperties.geocoding().dapi()).build();
        this.clientId = kakaoProperties.clientId();
        this.geocodeApiUrl = kakaoProperties.geocoding().geocodeApi();
    }

    @Override
    public Mono<GeocodeResponse> geocode(String address) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(geocodeApiUrl)
                        .queryParam("query", address)
                        .build())
                .header("Authorization", "KakaoAK " + clientId)
                .retrieve()
                .onStatus(
                        HttpStatusCode::isError,
                        response -> response.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Kakao Geocoding API error: status={}, body={}", response.statusCode(), errorBody);
                                    if (response.statusCode().is4xxClientError()) {
                                        return Mono.error(new BusinessException(ErrorMessage.INVALID_ADDRESS, "잘못된 주소로 요청했습니다."));
                                    }
                                    return Mono.error(new BusinessException(ErrorMessage.FAILED_GEOCODING_API));
                                })
                )
                .bodyToMono(KakaoGeocodeResponse.class)
                .flatMap(this::extractFirstDocument)
                .map(this::convertToGeocodeResponse)
                .doOnError(e -> log.error("[Kakao Geocoding] 주소 변환 실패: {}, 에러: {}", address, e.getMessage()));
    }

    /**
     * API 응답 맵에서 첫 번째 결과(Document)를 안전하게 추출합니다.
     */
    private Mono<KakaoGeocodeResponse.Document> extractFirstDocument(KakaoGeocodeResponse response) {
        if (response.documents() == null || response.documents().isEmpty()) {
            return Mono.error(new BusinessException(ErrorMessage.INVALID_ADDRESS, "해당 주소에 대한 검색 결과가 없습니다."));
        }
        return Mono.just(response.documents().get(0));
    }


    private GeocodeResponse convertToGeocodeResponse(KakaoGeocodeResponse.Document document) {
        return new GeocodeResponse(
                parseCoordinate(document.y(), "latitude"),
                parseCoordinate(document.x(), "longitude"),
                document.addressName()
        );
    }

    private double parseCoordinate(String value, String fieldName) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException | NullPointerException e) {
            throw new BusinessException(ErrorMessage.EXTERNAL_API_SERVER_ERROR, fieldName + " 파싱 실패: " + value);
        }
    }
}