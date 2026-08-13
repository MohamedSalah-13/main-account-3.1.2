package com.hamza.controlsfx.error;

import com.hamza.controlsfx.database.DaoException;

/** An expected, user-correctable input validation failure. */
public class UserValidationException extends DaoException implements UserFacingException {

    public UserValidationException(String message) {
        super(requireMessage(message));
    }

    public UserValidationException(String message, Throwable cause) {
        super(requireMessage(message), cause);
    }

    @Override
    public final ErrorCategory category() {
        return ErrorCategory.VALIDATION;
    }

    @Override
    public final String userMessage() {
        return getMessage();
    }

    private static String requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("A validation message must not be blank");
        }
        return message.trim();
    }
}
