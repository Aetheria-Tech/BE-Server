package com.serverbe.domain.exception.server;

import com.serverbe.domain.exception.BusinessException;
import com.serverbe.domain.exception.ErrorCode;

public class DataIntegrityViolationException extends BusinessException {

    protected DataIntegrityViolationException(ErrorCode errorCode) {
        super(errorCode);
    }

    public DataIntegrityViolationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}