package com.serverbe.application.port.in.dto.geocoding;

public record GeocodeResponse(
        Double latitude,
        Double longitude,
        String formattedAddress
) {
}