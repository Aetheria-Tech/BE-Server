package com.serverbe.adapter.in.web.dto.geocode;

import com.serverbe.application.port.out.dto.geocoding.GeocodeResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record GeocodeResponse(
        @Schema(description = "위도 (Latitude)", example = "37.5665")
        Double latitude,

        @Schema(description = "경도 (Longitude)", example = "126.9780")
        Double longitude,

        @Schema(description = "정제된 전체 주소", example = "서울특별시 중구 세종대로 110")
        String formattedAddress
) {
    /**
     * 내부 비즈니스 결과(Result)를 외부 응답(Response)으로 변환합니다.
     */
    public static GeocodeResponse toResponse(GeocodeResult result) {
        return new GeocodeResponse(result.latitude(), result.longitude(), result.formattedAddress());
    }
}