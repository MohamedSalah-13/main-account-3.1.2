package com.hamza.account.features.dbsetup;

/** A localized failure boundary: the UI resolves messageKey at the point of display. */
public final class DatabaseSetupException extends Exception {

    private final String messageKey;

    public DatabaseSetupException(String messageKey) {
        super(messageKey);
        this.messageKey = messageKey;
    }

    public DatabaseSetupException(String messageKey, Throwable cause) {
        super(messageKey, cause);
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }
}
