package com.hamza.account.features.productprofile;

import com.hamza.controlsfx.error.ErrorCategory;
import com.hamza.controlsfx.error.UserFacingException;
import com.hamza.controlsfx.language.LanguageManager;

/** Expected profile validation/import failure; the JavaFX boundary localizes its key. */
public final class ProductProfileException extends Exception implements UserFacingException {

    private final String messageKey;
    private final Object[] arguments;

    public ProductProfileException(String messageKey, Object... arguments) {
        super(messageKey);
        this.messageKey = messageKey;
        this.arguments = arguments == null ? new Object[0] : arguments.clone();
    }

    public ProductProfileException(String messageKey, Throwable cause, Object... arguments) {
        super(messageKey, cause);
        this.messageKey = messageKey;
        this.arguments = arguments == null ? new Object[0] : arguments.clone();
    }

    public String messageKey() {
        return messageKey;
    }

    public Object[] arguments() {
        return arguments.clone();
    }

    @Override
    public ErrorCategory category() {
        return ErrorCategory.BUSINESS;
    }

    @Override
    public String userMessage() {
        return LanguageManager.getInstance().getString(messageKey, arguments);
    }
}
