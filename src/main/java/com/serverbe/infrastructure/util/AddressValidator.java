package com.serverbe.infrastructure.util;

import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 주소 입력값의 유효성을 검증하는 유틸리티 클래스입니다.
 * 헥사고날 가이드에 따라 도메인/애플리케이션 계층에서 비즈니스 규칙 검증을 보조합니다.
 */
@UtilityClass
public class AddressValidator {

    // 특수문자만으로 이루어진 주소 방지 (한글, 영문, 숫자, 공백, 일부 허용 특수문자 -, () 만 허용)
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("^[a-zA-Z0-9가-힣\\s\\-\\(\\)]+$");

    /**
     * 지오코딩 요청 전 주소 문자열을 검증합니다.
     * @param address 검증할 주소 문자열
     * @throws BusinessException 유효하지 않은 주소일 경우 발생
     */
    public static void validate(String address) {
        // 1. 빈 값 및 공백 체크
        if (!StringUtils.hasText(address)) {
            throw new BusinessException(ErrorMessage.INVALID_ADDRESS, "주소를 입력해주세요.");
        }

        String trimmedAddress = address.trim();

        // 2. 최소 길이 체크 (지오코딩을 위한 최소한의 정보, 예: '서울시' 등 3자 이상 권장)
        if (trimmedAddress.length() < 2) {
            throw new BusinessException(ErrorMessage.INVALID_ADDRESS, "주소가 너무 짧습니다. 보다 구체적인 주소를 입력해주세요.");
        }

        // 3. 특수문자 패턴 체크 (SQL Injection 및 잘못된 요청 방지)
        if (!ADDRESS_PATTERN.matcher(trimmedAddress).matches()) {
            throw new BusinessException(ErrorMessage.INVALID_ADDRESS, "주소에 허용되지 않는 특수문자가 포함되어 있습니다.");
        }

        // 4. 지나치게 긴 주소 체크 (카카오 API 제한 및 서버 부하 방지)
        if (trimmedAddress.length() > 100) {
            throw new BusinessException(ErrorMessage.INVALID_ADDRESS, "주소의 길이가 너무 깁니다.");
        }
    }
}