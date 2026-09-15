package com.hamza.account.features.users;

/**
 * What the recovery window tells the operator about a pasted response, while it is pasted.
 *
 * <p>Advice, not a decision: {@link SupportRecoveryService#redeem} checks all of it again
 * against the stored row. A response that parses is still submittable when this says it is
 * for another request - pressing on is then recorded as a refused attempt, which is exactly
 * what {@code support_recovery_audit} is there to show. Only text that is not a response at
 * all is held back, because there is nothing there to submit.
 *
 * <p>The message keys are reached through {@link #messageKey()}, a variable, so
 * {@code MessageKeyArchitectureTest} cannot see them; {@code SupportRecoveryResponseCheckTest}
 * checks them against the three bundles instead.
 */
public enum SupportRecoveryResponseCheck {

    EMPTY("support.recovery.check.empty", false),
    INCOMPLETE("support.recovery.check.incomplete", false),
    NOT_SIGNED("support.recovery.check.unsigned", true),
    OTHER_REQUEST("support.recovery.check.other", true),
    MATCHES("support.recovery.check.ok", true);

    private final String messageKey;
    private final boolean submittable;

    SupportRecoveryResponseCheck(String messageKey, boolean submittable) {
        this.messageKey = messageKey;
        this.submittable = submittable;
    }

    public static SupportRecoveryResponseCheck of(String text, SupportRecoveryChallenge challenge,
                                                  SupportRecoverySigner.Verifier trusted) {
        if (text == null || text.isBlank()) return EMPTY;
        SupportRecoveryResponse response = SupportRecoveryResponse.parse(text).orElse(null);
        if (response == null) return INCOMPLETE;
        if (!trusted.verifies(response.payload(), response.signature())) return NOT_SIGNED;
        return response.answers(challenge) ? MATCHES : OTHER_REQUEST;
    }

    public String messageKey() {
        return messageKey;
    }

    /** Whether there is anything to hand to the service at all. */
    public boolean submittable() {
        return submittable;
    }
}
