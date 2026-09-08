package com.hamza.account.features.users;

/** Informational strength shown by the password-change form; only eight characters are policy. */
public enum PasswordStrength {
    EMPTY(0.0, "password.change.strength.empty", "strength-empty"),
    WEAK(0.25, "password.change.strength.weak", "strength-weak"),
    FAIR(0.50, "password.change.strength.fair", "strength-fair"),
    GOOD(0.75, "password.change.strength.good", "strength-good"),
    STRONG(1.0, "password.change.strength.strong", "strength-strong");

    private final double progress;
    private final String messageKey;
    private final String styleClass;

    PasswordStrength(double progress, String messageKey, String styleClass) {
        this.progress = progress;
        this.messageKey = messageKey;
        this.styleClass = styleClass;
    }

    public double progress() {
        return progress;
    }

    public String messageKey() {
        return messageKey;
    }

    public String styleClass() {
        return styleClass;
    }
}
