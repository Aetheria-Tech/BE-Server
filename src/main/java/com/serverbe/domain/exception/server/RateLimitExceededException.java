package com.serverbe.domain.exception.server;

import com.serverbe.domain.exception.ErrorCode;
import lombok.Getter;

/**
 * 429 Too Many Requests 발생 시, 재시도 대기 시간(초)을 함께 전달하기 위한 특화 예외입니다.
 */
@Getter
public class RateLimitExceededException extends ServerException {
    
    private final int retryAfterSeconds;

    public RateLimitExceededException(ErrorCode errorCode, int retryAfterSeconds) {
        super(errorCode);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}