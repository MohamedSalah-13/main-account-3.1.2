package com.hamza.account.features.users;

import com.hamza.controlsfx.database.DaoException;

/** An expected password-form refusal whose message is resolved at the JavaFX boundary. */
public final class PasswordChangeException extends DaoException {

    private final String messageKey;

    public PasswordChangeException(String messageKey) {
        super(messageKey);
        this.messageKey = requireMessageKey(messageKey);
    }

    public PasswordChangeException(String messageKey, Throwable cause) {
        super(messageKey, cause);
        this.messageKey = requireMessageKey(messageKey);
    }

    private static String requireMessageKey(String messageKey) {
        if (messageKey == null || messageKey.isBlank()) {
            throw new IllegalArgumentException("A password-change message key is required");
        }
        return messageKey;
    }

    public String messageKey() {
        return messageKey;
    }
}
