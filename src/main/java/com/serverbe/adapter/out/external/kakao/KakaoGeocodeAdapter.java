package com.serverbe.adapter.out.external.kakao;

import com.serverbe.adapter.out.external.kakao.dto.KakaoGeocodeResponse;
import com.serverbe.application.port.out.dto.geocoding.GeocodeResult;
import com.serverbe.application.port.out.geocode.GeocodePort;
import com.serverbe.domain.exception.external.ExternalApiErrorCode;
import com.serverbe.domain.exception.external.ExternalApiException;
import com.serverbe.infrastructure.config.properties.KakaoProperties;
import com.serverbe.domain.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * @author Duskafka
 * @responsibility 카카오 로컬(Local) 외부 API를 호출하여 주소 문자열을 위경도 좌표로 변환(Geocoding)하는 역할을 수행합니다.
 * @implSpec Spring WebClient를 사용하여 비동기 논블로킹(Non-blocking) 방식으로 HTTP 통신을 수행하며, {@link GeocodePort} 인터페이스를 구현합니다.
 */
@Slf4j
@Component
public class KakaoGeocodeAdapter implements GeocodePort {

    private final WebClient webClient;
    private final String clientId;
    private final String geocodeApiUrl;

    /**
     * @param builder         WebClient 구성을 위한 {@link WebClient.Builder}
     * @param kakaoProperties 카카오 API 관련 설정 정보를 담은 {@link KakaoProperties}
     * @implSpec 1. {@link KakaoProperties}에서 제공하는 DAPI 호스트 정보를 기반으로 {@link WebClient}의 BaseURL을 설정하여 빌드합니다.<br>
     * 2. 외부 API 호출 시 필요한 클라이언트 ID와 지오코딩 엔드포인트 경로를 필드에 할당하여 초기화합니다.
     */
    public KakaoGeocodeAdapter(
            WebClient.Builder builder,
            KakaoProperties kakaoProperties
    ) {
        this.webClient = builder.baseUrl(kakaoProperties.geocoding().dapi()).build();
        this.clientId = kakaoProperties.clientId();
        this.geocodeApiUrl = kakaoProperties.geocoding().geocodeApi();
    }

    /**
     * @param address 변환하고자 하는 한글 주소 문자열
     * @return 지오코딩 결과를 담은 비동기 스트림 {@link Mono<GeocodeResult>}
     * @responsibility 입력된 주소를 바탕으로 카카오 지오코딩 API를 호출하여 좌표 정보를 반환합니다.
     * @implSpec 1. GET 요청을 통해 주소 쿼리를 전달하며, 헤더에 'KakaoAK' 인증 키를 포함합니다.<br>
     * 2. HTTP 상태 코드가 에러(4xx, 5xx)인 경우 이를 가로채 서비스 전용 예외인 {@link BusinessException}으로 변환합니다.
     * @implNote 4xx 에러 발생 시 사용자가 잘못된 주소를 입력한 것으로 간주하여 {@link ExternalApiErrorCode#INVALID_ADDRESS}를 반환합니다.
     */
    @Override
    public Mono<GeocodeResult> geocode(String address) {
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
                                        return Mono.error(new ExternalApiException(ExternalApiErrorCode.INVALID_ADDRESS, "잘못된 주소로 요청했습니다."));
                                    }
                                    return Mono.error(new ExternalApiException(ExternalApiErrorCode.FAILED_GEOCODING_API));
                                })
                )
                .bodyToMono(KakaoGeocodeResponse.class)
                .flatMap(this::extractFirstDocument)
                .map(this::convertToGeocodeResponse)
                .doOnError(e -> log.error("[Kakao Geocoding] 주소 변환 실패: {}, 에러: {}", address, e.getMessage()));
    }

    /**
     * @param response 카카오 API로부터 수신한 전체 응답 객체 {@link KakaoGeocodeResponse}
     * @return 첫 번째 결과 정보만을 담은 {@link Mono<KakaoGeocodeResponse.Document>}
     * @responsibility API 응답 결과 리스트에서 최상단에 위치한 첫 번째 데이터를 추출합니다.
     * @implNote 검색 결과(Documents)가 null이거나 비어있을 경우, 검색 결과가 없는 것으로 판단하여 예외를 발생시킵니다.
     */
    private Mono<KakaoGeocodeResponse.Document> extractFirstDocument(KakaoGeocodeResponse response) {
        if (response.documents() == null || response.documents().isEmpty()) {
            return Mono.error(new ExternalApiException(ExternalApiErrorCode.INVALID_ADDRESS, "해당 주소에 대한 검색 결과가 없습니다."));
        }
        return Mono.just(response.documents().get(0));
    }

    /**
     * @param document 카카오 응답 내의 개별 문서 객체 {@link KakaoGeocodeResponse.Document}
     * @return 위도, 경도, 주소명이 포함된 {@link GeocodeResult}
     * @responsibility 카카오 API 전용 응답 객체를 시스템 공통 지오코딩 결과 객체로 변환합니다.
     */
    private GeocodeResult convertToGeocodeResponse(KakaoGeocodeResponse.Document document) {
        return new GeocodeResult(
                parseCoordinate(document.y(), "latitude"),
                parseCoordinate(document.x(), "longitude"),
                document.addressName()
        );
    }

    /**
     * @param value     변환할 문자열 좌표 값
     * @param fieldName 에러 로깅을 위한 필드 명칭 (latitude/longitude)
     * @return 변환된 double 타입의 좌표값
     * @responsibility 문자열로 전달된 좌표 값을 숫자(double) 형으로 변환하며 유효성을 검사합니다.
     * @implNote 숫자 형식이 아니거나 데이터가 누락된 경우 {@link BusinessException}을 발생시켜 외부 API 서버 오류로 처리합니다.
     */
    private double parseCoordinate(String value, String fieldName) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException | NullPointerException e) {
            throw new ExternalApiException(ExternalApiErrorCode.EXTERNAL_API_SERVER_ERROR, fieldName + " 파싱 실패: " + value);
        }
    }
}