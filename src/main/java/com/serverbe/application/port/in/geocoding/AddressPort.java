package com.serverbe.application.port.in.geocoding;

import com.serverbe.application.port.in.dto.geocoding.GeocodeResponse;
import reactor.core.publisher.Mono;

/**
 * 주소 관련 외부 서비스와 통신하기 위한 출력 포트입니다.
 */
public interface AddressPort {
    /**
     * 주소 문자열을 위도와 경도로 변환합니다.
     * @param address 주소 (예: 서울특별시 강남구 ...)
     * @return 위도/경도 정보를 담은 Mono
     */
    Mono<GeocodeResponse> geocode(String address);
}