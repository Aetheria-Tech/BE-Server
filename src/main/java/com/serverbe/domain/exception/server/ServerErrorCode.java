package com.serverbe.domain.exception.server;

import com.serverbe.domain.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ServerErrorCode implements ErrorCode {
    INVALID_REQUEST_PARAMETER(HttpStatus.BAD_REQUEST, "COMMON_001", "잘못된 요청 파라미터입니다."),
    MALFORMED_JSON_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_002", "잘못된 형식의 JSON 요청입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_003", "서버 내부 오류가 발생했습니다."),
    DE_IDENTIFIED_DEVICES(HttpStatus.BAD_REQUEST, "COMMON_004", "Device ID를 식별할 수 없습니다. X-Device-Id 또는 User-Agent 헤더를 확인해주세요."),
    UTILITY_CLASS(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_005", "코드 내부에서 유틸리티 클래스를 생성자로 생성합니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "COMMON_006", "너무 많은 요청이 들어왔습니다. 잠시 후에 요청을 보내주세요."),
    HASHING_ALGORITHM_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_007", "해싱 알고리즘을 찾지 못했습니다.")

    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}