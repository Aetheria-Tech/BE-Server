package com.serverbe.application.port.out.dto.geocoding;

public record GeocodeResult(
        Double latitude,
        Double longitude,
        String formattedAddress
) {
}