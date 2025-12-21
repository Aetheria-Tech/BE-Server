package com.serverbe.application.port.out.dto.geocoding;

public record GeocodeResponse(
        Double latitude,
        Double longitude,
        String formattedAddress
) {
}