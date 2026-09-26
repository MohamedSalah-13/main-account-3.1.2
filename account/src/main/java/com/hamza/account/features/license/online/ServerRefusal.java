package com.hamza.account.features.license.online;

/**
 * Why the licence server said no - its closed list of error codes ({@code server-plan.md} §5.2), plus
 * {@link #UNEXPECTED} for an answer that is not the contract at all.
 * <p>
 * The server answers a code, never a sentence, and the program says it in the user's language: every
 * constant has a message key, written whole so a search finds it. {@code ServerRefusalTest} holds the
 * list to the server's fourteen names exactly and each key to the three bundles - a code the server adds
 * later arrives here as {@link #UNEXPECTED} until a release translates it.
 */
public enum ServerRefusal {

    INVALID_REQUEST,
    UNSUPPORTED_SCHEMA,
    INVALID_CODE_FORMAT,
    UNKNOWN_CODE,
    LICENSE_SUSPENDED,
    LICENSE_REVOKED,
    /** Carries {@code seatsUsed} and {@code seatsTotal}; its message takes both. */
    SEATS_FULL,
    MACHINE_ID_INVALID,
    MACHINE_BELONGS_TO_ANOTHER_CUSTOMER,
    SIGNATURE_INVALID,
    NOT_ACTIVATED,
    ACTIVATION_RELEASED,
    RATE_LIMITED,
    /** Carries a {@code reference} for support; its message takes it. */
    SERVER_ERROR,
    /** Not the server's: an answer that could not be read as the contract, or a code this build does not know. */
    UNEXPECTED;

    /** The constant for a code the server sent, {@link #UNEXPECTED} for one this build does not know. */
    public static ServerRefusal of(String code) {
        if (code == null || code.equals(UNEXPECTED.name())) {
            return UNEXPECTED;
        }
        for (ServerRefusal refusal : values()) {
            if (refusal.name().equals(code)) {
                return refusal;
            }
        }
        return UNEXPECTED;
    }

    /** What to tell the user. {@link #SEATS_FULL} takes two {@code %d}, {@link #SERVER_ERROR} one {@code %s}. */
    public String messageKey() {
        return switch (this) {
            case INVALID_REQUEST, UNSUPPORTED_SCHEMA -> "license.online.refused.outdated";
            case INVALID_CODE_FORMAT -> "license.online.refused.code.format";
            case UNKNOWN_CODE -> "license.online.refused.code.unknown";
            case LICENSE_SUSPENDED -> "license.online.refused.suspended";
            case LICENSE_REVOKED -> "license.online.refused.revoked";
            case SEATS_FULL -> "license.online.refused.seats.full";
            case MACHINE_ID_INVALID -> "license.online.refused.machine.invalid";
            case MACHINE_BELONGS_TO_ANOTHER_CUSTOMER -> "license.online.refused.machine.other.customer";
            case SIGNATURE_INVALID -> "license.online.refused.signature";
            case NOT_ACTIVATED -> "license.online.refused.not.activated";
            case ACTIVATION_RELEASED -> "license.online.refused.released";
            case RATE_LIMITED -> "license.online.refused.rate.limited";
            case SERVER_ERROR -> "license.online.refused.server.error";
            case UNEXPECTED -> "license.online.refused.unexpected";
        };
    }
}
