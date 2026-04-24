package com.serverbe.infrastructure.util;

import com.serverbe.domain.exception.server.ServerErrorCode;
import com.serverbe.domain.exception.server.ServerException;

public final class PolylineUtils {

    private static final double PRECISION = 1e5; // 100,000
    private static final double EARTH_RADIUS_METERS = 6371000.0; // 지구 반지름 (미터)

    /**
     * 해독 결과를 담을 Record (Java 16+)
     */
    public record PolylineMetadata(
            double startLat,
            double startLon,
            double totalDistanceMeters // 총 거리 (미터 단위)
    ) {
    }

    /**
     * Encoded Polyline 문자열을 끝까지 해독하여 첫 번째 좌표와 전체 거리를 계산합니다.
     */
    public static PolylineMetadata extractMetadata(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            throw new IllegalArgumentException("Encoded polyline string cannot be null or empty");
        }

        int len = encoded.length();
        int[] index = {0};

        int currentLatE5 = 0;
        int currentLngE5 = 0;

        Double startLat = null;
        Double startLon = null;

        double prevLat = 0.0;
        double prevLon = 0.0;
        double totalDistance = 0.0;

        while (index[0] < len) {
            currentLatE5 += decodeInt(encoded, index);
            currentLngE5 += decodeInt(encoded, index);

            double lat = currentLatE5 / PRECISION;
            double lon = currentLngE5 / PRECISION;

            if (startLat == null) {
                startLat = lat;
                startLon = lon;
            } else {
                totalDistance += calculateHaversineDistance(prevLat, prevLon, lat, lon);
            }

            prevLat = lat;
            prevLon = lon;
        }

        // 루프를 다 돌았는데도 시작 좌표가 null이라면, 유효한 좌표가 없는 문자열입니다.
        // 이 처리를 통해 Record의 원시 타입(double)으로 Auto-unboxing 될 때 발생하는 NPE를 방지합니다.
        if (startLat == null || startLon == null) {
            throw new ServerException(
                    ServerErrorCode.INTERNAL_SERVER_ERROR,
                    "Invalid encoded polyline string: no points found"
            );
        }

        return new PolylineMetadata(startLat, startLon, totalDistance);
    }

    /**
     * 폴리라인 알고리즘의 핵심: 인코딩된 문자열에서 하나의 정수값을 뽑아냅니다.
     */
    private static int decodeInt(String encoded, int[] index) {
        int result = 0;
        int shift = 0;
        int b;
        do {
            if (index[0] >= encoded.length()) {
                throw new ServerException(
                        ServerErrorCode.INTERNAL_SERVER_ERROR,
                        "Truncated polyline string"
                );
            }

            b = encoded.charAt(index[0]++) - 63;
            result |= (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20);

        return ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
    }

    /**
     * 하버사인(Haversine) 공식을 사용하여 두 위경도 좌표 사이의 거리를 계산합니다 (미터 단위).
     */
    private static double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double rLat1 = Math.toRadians(lat1);
        double rLat2 = Math.toRadians(lat2);

        double sinDLat = Math.sin(dLat / 2);
        double sinDLon = Math.sin(dLon / 2);
        double a = sinDLat * sinDLat +
                Math.cos(rLat1) * Math.cos(rLat2) * sinDLon * sinDLon;

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_METERS * c;
    }
}