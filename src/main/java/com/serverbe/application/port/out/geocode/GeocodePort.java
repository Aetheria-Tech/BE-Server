package com.serverbe.application.port.out.geocode;

import com.serverbe.application.port.out.dto.geocoding.GeocodeResult;
import reactor.core.publisher.Mono;

/**
 * 주소 관련 외부 서비스와 통신하기 위한 출력 포트입니다.
 */
public interface GeocodePort {
    /**
     * 주소 문자열을 위도와 경도로 변환합니다.
     * @param address 주소 (예: 서울특별시 강남구 ...)
     * @return 위도/경도 정보를 담은 Mono
     */
    Mono<GeocodeResult> geocode(String address);
}