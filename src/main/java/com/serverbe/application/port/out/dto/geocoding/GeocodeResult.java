package com.serverbe.application.port.out.dto.geocoding;

/**
 * @param latitude         위도 좌표
 * @param longitude        경도 좌표
 * @param formattedAddress 정제된 주소 명칭
 * @responsibility 외부 지오코딩 서비스를 통해 변환된 위치 정보를 전달하는 객체입니다.
 */
public record GeocodeResult(
        Double latitude,
        Double longitude,
        String formattedAddress
) {
}