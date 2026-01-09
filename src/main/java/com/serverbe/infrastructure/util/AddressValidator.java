package com.serverbe.infrastructure.util;

import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * @responsibility 외부 지오코딩 API 요청 전, 입력받은 주소 문자열의 <b>형식적 유효성 및 보안성</b>을 검증합니다.
 * @implSpec
 * 1. <b>정규 표현식</b>을 사용하여 허용되지 않는 특수문자(SQL Injection 위험 요소 등)를 차단합니다.<br>
 * 2. 헥사고날 아키텍처 관점에서 인프라 서비스나 애플리케이션 서비스가 비즈니스 규칙을 준수하도록 보조합니다.<br>
 * 3. {@link UtilityClass} 어노테이션을 통해 인스턴스화를 방지하고 정적 메서드만을 제공합니다.
 */
@UtilityClass
public class AddressValidator {

    /**
     * 주소 허용 패턴: 한글, 영문, 숫자, 공백, 하이픈(-), 괄호(())만 허용합니다.
     * 특수문자만을 이용한 악성 입력을 방지하기 위함입니다.
     */
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("^[a-zA-Z0-9가-힣\\s\\-\\(\\)]+$");

    /**
     * @responsibility 주소 문자열을 다각도로 검증하여 부적절한 요청을 사전에 차단합니다.
     * @implNote
     * 1. <b>공백 검사</b>: 비어있는 문자열이나 공백만 있는 경우를 확인합니다.<br>
     * 2. <b>길이 검사</b>: 지오코딩이 가능한 최소 정보(3자)와 시스템 부하 방지를 위한 최대 길이(100자)를 제한합니다.<br>
     * 3. <b>패턴 검사</b>: 정의된 {@code ADDRESS_PATTERN}과 일치하지 않는 특수문자가 포함된 경우 예외를 던집니다.
     * @param address 검증 대상 주소 문자열
     * @throws BusinessException {@link ErrorMessage#INVALID_ADDRESS}를 포함하며, 구체적인 위반 사유를 메시지에 담습니다.
     */
    public static void validate(String address) {
        // 1. 빈 값 및 공백 체크
        if (!StringUtils.hasText(address)) {
            throw new BusinessException(ErrorMessage.INVALID_ADDRESS, "주소를 입력해주세요.");
        }

        String trimmedAddress = address.trim();

        // 2. 최소 길이 체크 (지오코딩을 위한 최소한의 정보, 예: '서울시' 등 3자 이상 권장)
        if (trimmedAddress.length() < 3) {
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