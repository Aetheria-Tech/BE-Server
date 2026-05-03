package com.serverbe.adapter.out.external.kakao;

import com.serverbe.application.port.out.dto.geocoding.GeocodeResult;
import com.serverbe.domain.exception.external.ExternalApiErrorCode;
import com.serverbe.domain.exception.external.ExternalApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 카카오 지오코딩 API 통신 실패 시 대체(Fallback) 동작을 처리하는 핸들러
 */
@Slf4j
@Component
public class KakaoGeocodeFallbackHandler {

    public Mono<GeocodeResult> fallbackGeocode(String address, Throwable t) {
        log.error("🚨 [CircuitBreaker/Timeout] 카카오 지오코딩 API 장애 발생 (요청 주소: {}): {}", address, t.getMessage());
        
        // 4xx 에러(잘못된 주소 등)는 서킷 브레이커를 거치면서 내려온 정상적인(예상된) 예외일 수 있으므로 그대로 던지고,
        // 그 외의 타임아웃/서킷오픈 등의 에러는 서버 지연 메시지로 래핑합니다.
        if (t instanceof ExternalApiException && ((ExternalApiException) t).getErrorCode() == ExternalApiErrorCode.INVALID_ADDRESS) {
            return Mono.error(t);
        }

        return Mono.error(new ExternalApiException(
                ExternalApiErrorCode.FAILED_GEOCODING_API,
                "지도 서버 응답이 지연되고 있습니다. 잠시 후 다시 시도해주세요."
        ));
    }
}