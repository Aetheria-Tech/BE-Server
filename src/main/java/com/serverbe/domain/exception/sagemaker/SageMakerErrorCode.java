package com.serverbe.domain.exception.sagemaker;

import com.serverbe.domain.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SageMakerErrorCode implements ErrorCode {
    SAGE_MAKER_ERROR_CODE(HttpStatus.INTERNAL_SERVER_ERROR, "SM_001","SageMaker 비동기 호출 중 오류가 발생했습니다."),


    ;



    private final HttpStatus status;
    private final String code;
    private final String message;
}