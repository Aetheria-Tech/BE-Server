package com.serverbe.adapter.in.web;

import com.serverbe.adapter.in.web.dto.geocode.GeocodeResponse;
import com.serverbe.application.port.out.geocode.GeocodePort;
import com.serverbe.domain.model.address.Address;
import com.serverbe.infrastructure.common.response.RestApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Tag(name = "Geocode", description = "주소 및 위치 관련 API")
@RestController
@RequestMapping("/api/v1/geocode")
@RequiredArgsConstructor
public class GeocodeController {

    private final GeocodePort geocodePort;

    @Operation(
            summary = "주소를 위경도로 변환 (지오코딩)",
            description = "텍스트 주소를 입력받아 해당 위치의 위도(latitude), 경도(longitude) 및 표준 주소를 반환합니다.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "변환 성공",
                            useReturnTypeSchema = true
                    ),
                    @ApiResponse(responseCode = "400", description = "유효하지 않은 주소 형식이거나 빈 값인 경우"),
                    @ApiResponse(responseCode = "500", description = "외부 지도 API 서버 에러 또는 위치를 찾을 수 없는 경우")
            }
    )
    @GetMapping
    public Mono<ResponseEntity<RestApiResponse<GeocodeResponse>>> geocode(
            @Parameter(description = "변환할 도로명 또는 지번 주소", example = "서울특별시 강남구 테헤란로 427", required = true)
            @RequestParam(name = "address") @NotBlank String address
    ) {
        new Address(address); // 유효성 검사 수행

        // 비동기 체인 유지 + ResponseEntity로 200 OK 헤더 명시
        return geocodePort.geocode(address)
                .map(GeocodeResponse::toResponse)
                .subscribeOn(Schedulers.boundedElastic())
                .map(response -> ResponseEntity.ok(RestApiResponse.success(response)));
    }
}