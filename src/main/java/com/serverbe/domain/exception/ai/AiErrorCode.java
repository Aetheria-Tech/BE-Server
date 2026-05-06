package com.serverbe.domain.exception.ai;

import com.serverbe.domain.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AiErrorCode implements ErrorCode {
    NOT_FOUND_AITASK(HttpStatus.NOT_FOUND, "AI_001", "작업을 찾을 수 없습니다."),
    AI_PIPELINE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "AI_002", "AI 생성 요청 처리 중 서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),
    USER_IS_NOT_OWNER_OF_TASK(HttpStatus.FORBIDDEN, "AI_003", "요청자는 이 작업을 조회할 권한이 없습니다"),
    DUPLICATE_AI_REQUEST(HttpStatus.TOO_MANY_REQUESTS, "AI_001", "이미 생성 중인 작업이 있습니다. 잠시만 기다려주세요."),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "AI_002", "요청이 너무 잦습니다. 5초 후에 다시 시도해주세요."),
    ;
    private final HttpStatus status;
    private final String code;
    private final String message;
}
