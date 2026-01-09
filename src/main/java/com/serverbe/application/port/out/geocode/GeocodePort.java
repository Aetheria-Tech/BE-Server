package com.serverbe.application.port.out.geocode;

import com.serverbe.application.port.out.dto.geocoding.GeocodeResult;
import reactor.core.publisher.Mono;

/**
 * @responsibility 주소 정보를 위도와 경도 좌표로 변환(Geocoding)하기 위한 아웃바운드 포트 인터페이스입니다.
 * 외부 지오코딩 서비스(Kakao, Google 등)와의 결합도를 낮추고 비즈니스 로직에 독립적인 좌표 변환 기능을 제공합니다.
 */
public interface GeocodePort {

    /**
     * @responsibility 전달받은 텍스트 형태의 주소를 분석하여 지리적 좌표 정보(위도, 경도)로 변환합니다.
     * @param address 변환하고자 하는 실제 주소 문자열 (예: 서울특별시 강남구 ...)
     * @return 지오코딩 결과 정보를 담고 있는 비동기 스트림 {@link Mono<GeocodeResult>}
     */
    Mono<GeocodeResult> geocode(String address);
}