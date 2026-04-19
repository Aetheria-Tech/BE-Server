package com.serverbe.domain.exception.ai;

import com.serverbe.domain.exception.BusinessException;
import com.serverbe.domain.exception.ErrorCode;

public class AiException extends BusinessException {
    public AiException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AiException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}