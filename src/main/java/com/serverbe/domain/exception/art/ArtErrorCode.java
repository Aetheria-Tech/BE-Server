package com.serverbe.domain.exception.art;

import com.serverbe.domain.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ArtErrorCode implements ErrorCode {
    NOT_FOUND_RUNNING_ART(HttpStatus.NOT_FOUND, "ART_001", "런닝아트를 찾을 수 없습니다"),
    USER_IS_NOT_OWNER_OF_RUNNING_ART(HttpStatus.FORBIDDEN, "ART_002", "사용자는 런닝아트의 소유자가 아닙니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}