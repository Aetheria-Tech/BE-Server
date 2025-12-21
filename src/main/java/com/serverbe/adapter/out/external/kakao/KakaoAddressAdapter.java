package com.serverbe.adapter.out.external.kakao;

import com.serverbe.application.port.in.dto.geocoding.GeocodeResponse;
import com.serverbe.application.port.in.geocoding.AddressPort;
import com.serverbe.infrastructure.config.properties.KakaoProperties;
import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 카카오 로컬 API를 사용하여 지오코딩을 수행하는 어댑터입니다.
 */
@Slf4j
@Component
public class KakaoAddressAdapter implements AddressPort {

    private final WebClient webClient;
    private final String CLIENT_ID;
    private final String GEOCODE_API_URL;

    public KakaoAddressAdapter(
            WebClient.Builder builder,
            KakaoProperties kakaoProperties
    ) {
        // baseUrl은 호스트까지만 설정하는 것이 관례이며, 나머지는 uri()에서 처리합니다.
        this.webClient = builder.baseUrl(kakaoProperties.geocoding().dapi()).build();
        this.CLIENT_ID = kakaoProperties.clientId();
        this.GEOCODE_API_URL = kakaoProperties.geocoding().geocodeApi();
    }

    @Override
    public Mono<GeocodeResponse> geocode(String address) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(GEOCODE_API_URL)
                        .queryParam("query", address)
                        .build())
                .header("Authorization", "KakaoAK " + CLIENT_ID)
                .retrieve()
                // ParameterizedTypeReference를 사용하여 Map<String, Object> 타입을 명확히 지정합니다.
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .flatMap(this::extractFirstDocument)
                .map(this::convertToGeocodeResponse)
                .doOnError(e -> log.error("[Kakao Geocoding] 주소 변환 실패: {}, 에러: {}", address, e.getMessage()));
    }

    /**
     * API 응답 맵에서 첫 번째 결과(Document)를 안전하게 추출합니다.
     */
    private Mono<Map<String, Object>> extractFirstDocument(Map<String, Object> response) {
        return Optional.ofNullable((List<Map<String, Object>>) response.get("documents"))
                .filter(docs -> !docs.isEmpty())
                .map(docs -> Mono.just(docs.get(0)))
                .orElseGet(() -> Mono.error(new BusinessException(ErrorMessage.FAILED_GEOCODING_API)));
    }

    /**
     * 추출된 Document 맵 데이터를 GeocodeResponse DTO로 변환합니다.
     */
    private GeocodeResponse convertToGeocodeResponse(Map<String, Object> document) {
        String x = String.valueOf(document.get("x")); // 경도
        String y = String.valueOf(document.get("y")); // 위도
        String addressName = (String) document.get("address_name");

        return new GeocodeResponse(
                Double.parseDouble(y),
                Double.parseDouble(x),
                addressName
        );
    }
}