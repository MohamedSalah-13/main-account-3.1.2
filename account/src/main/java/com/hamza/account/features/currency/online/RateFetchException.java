package com.hamza.account.features.currency.online;

import java.util.Objects;

/**
 * Why a source gave no rates. Not user-facing itself: {@link OnlineRateService} turns the kind into the
 * sentence the screen shows, after every source has been tried.
 */
public final class RateFetchException extends Exception {

    /** What went wrong, in the three shapes the screen tells apart. */
    public enum Kind {
        /** No answer at all: no connection, a name that does not resolve, a timeout. */
        OFFLINE,
        /** The source answered that it does not know the base currency. */
        BASE_NOT_OFFERED,
        /** The source answered with something that is not rates: an error page, a limit, a broken body. */
        SOURCE
    }

    private final Kind kind;

    public RateFetchException(Kind kind, String message) {
        super(message);
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public RateFetchException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public Kind kind() {
        return kind;
    }
}
