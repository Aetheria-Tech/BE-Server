package com.serverbe.domain.exception.external;

import com.serverbe.domain.exception.BusinessException;
import com.serverbe.domain.exception.ErrorCode;

public class ExternalApiClientException extends BusinessException {
    protected ExternalApiClientException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ExternalApiClientException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}