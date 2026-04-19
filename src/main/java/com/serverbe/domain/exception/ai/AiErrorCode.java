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

    ;
    private final HttpStatus status;
    private final String code;
    private final String message;
}
