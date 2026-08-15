package com.hamza.controlsfx.util.crypto;

import com.hamza.controlsfx.error.ErrorCategory;
import com.hamza.controlsfx.error.UserFacingException;

public class CryptoException extends Exception implements UserFacingException {

    public CryptoException() {
    }

    public CryptoException(String message, Throwable throwable) {
        super(message, throwable);
    }

    @Override
    public ErrorCategory category() {
        return ErrorCategory.TECHNICAL;
    }

    @Override
    public String userMessage() {
        return getMessage();
    }
}
