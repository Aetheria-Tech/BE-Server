package com.serverbe.adapter.in.web;

import com.serverbe.application.port.out.dto.geocoding.GeocodeResponse;
import com.serverbe.application.port.out.geocoding.AddressPort;
import com.serverbe.infrastructure.common.ApiResponse;
import com.serverbe.infrastructure.util.AddressValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Tag(name = "Address", description = "주소 및 위치 관련 API")
@RestController
@RequestMapping("/api/v1/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressPort addressPort;

    @Operation(summary = "주소를 위경도로 변환 (지오코딩)")
    @GetMapping("/geocode")
    public Mono<ApiResponse<GeocodeResponse>> geocode(@RequestParam(name = "address") String address) {
        AddressValidator.validate(address);
        // 외부 API 호출이므로 비동기 체인(Mono)을 그대로 반환합니다.
        return addressPort.geocode(address)
                .map(ApiResponse::success);
    }
}