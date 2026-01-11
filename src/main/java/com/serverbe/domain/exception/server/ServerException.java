package com.serverbe.domain.exception.server;

import com.serverbe.domain.exception.BusinessException;
import com.serverbe.domain.exception.ErrorCode;

public class ServerException extends BusinessException {
    public ServerException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ServerException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}