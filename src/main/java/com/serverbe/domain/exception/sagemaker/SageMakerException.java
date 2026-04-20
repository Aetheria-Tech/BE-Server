package com.serverbe.domain.exception.sagemaker;

import com.serverbe.domain.exception.BusinessException;
import com.serverbe.domain.exception.ErrorCode;

public class SageMakerException extends BusinessException {
    public SageMakerException(ErrorCode errorCode) {
        super(errorCode);
    }

    public SageMakerException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}