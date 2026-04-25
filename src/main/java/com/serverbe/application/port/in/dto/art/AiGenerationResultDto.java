package com.serverbe.application.port.in.dto.art;

/**
 * AI 서버가 S3에 떨궈주는 결과물 JSON 포맷
 */
public record AiGenerationResultDto(
        Double startLat,
        Double startLon,
        Double distance,
        String gpx // Polyline 형태의 문자열
) {}