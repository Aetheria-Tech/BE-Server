package com.serverbe.domain.exception.server;

import com.serverbe.domain.exception.BusinessException;
import com.serverbe.domain.exception.ErrorCode;

public class ServerExcepetion extends BusinessException {
    public ServerExcepetion(ErrorCode errorCode) {
        super(errorCode);
    }

    public ServerExcepetion(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}