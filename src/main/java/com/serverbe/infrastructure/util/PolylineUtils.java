package com.serverbe.infrastructure.util;

public final class PolylineUtils {

    private static final double PRECISION = 1e5; // 100,000

    /**
     * Encoded Polyline 문자열에서 첫 번째 좌표(위경도)만 빠르게 추출합니다.
     */
    public static double[] decodeFirstLocation(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            throw new IllegalArgumentException("Encoded polyline string cannot be null or empty");
        }

        int[] index = {0}; // 가변 인덱스를 전달하기 위해 배열 사용

        // 1. 위도(Latitude) 해독
        int latE5 = decodeInt(encoded, index);

        // 2. 경도(Longitude) 해독 (위도 해독 후 인덱스가 유지됨)
        int lngE5 = decodeInt(encoded, index);

        return new double[]{latE5 / PRECISION, lngE5 / PRECISION};
    }

    /**
     * 폴리라인 알고리즘의 핵심: 인코딩된 문자열에서 하나의 정수값을 뽑아냅니다.
     * @param encoded 전체 문자열
     * @param index 현재 읽고 있는 위치 (배열을 사용하여 참조 전달 효과)
     * @return 해독된 정수값 (5단계 시프트 및 63 차감 등이 적용된 결과)
     */
    private static int decodeInt(String encoded, int[] index) {
        int result = 0;
        int shift = 0;
        int b;

        do {
            // 인코딩 시 사용된 63(ASCII '?')을 차감
            b = encoded.charAt(index[0]++) - 63;
            // 하위 5비트만 취해서 결과값에 시프트하여 더함
            result |= (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20); // 0x20(32)보다 크면 뒤에 비트가 더 있다는 뜻

        // 마지막 비트가 1이면 음수, 0이면 양수로 변환 (ZigZag Decoding)
        return ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
    }
}