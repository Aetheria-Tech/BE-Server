package com.serverbe.domain.exception.crypto;

import com.serverbe.domain.exception.BusinessException;
import com.serverbe.domain.exception.ErrorCode;

public class CryptoException extends BusinessException {
    public CryptoException(ErrorCode errorCode) {
        super(errorCode);
    }
}