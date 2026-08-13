package com.hamza.controlsfx.error;

import com.hamza.controlsfx.database.DaoException;

/** An expected refusal caused by a domain rule or the current business state. */
public class BusinessRuleException extends DaoException implements UserFacingException {

    public BusinessRuleException(String message) {
        super(requireMessage(message));
    }

    public BusinessRuleException(String message, Throwable cause) {
        super(requireMessage(message), cause);
    }

    @Override
    public final ErrorCategory category() {
        return ErrorCategory.BUSINESS;
    }

    @Override
    public final String userMessage() {
        return getMessage();
    }

    private static String requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("A business-rule message must not be blank");
        }
        return message.trim();
    }
}
