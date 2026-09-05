package com.hamza.account.features.pricecheck;

/**
 * Which scan the screen is currently showing the answer to.
 * <p>
 * A lookup runs off the JavaFX thread, so two scans in quick succession are two queries
 * in flight at once and nothing says the first one finishes first. Without this, a slow
 * answer to the packet already put down overwrites the fast answer to the one the
 * customer is holding - and the screen looks right while showing the wrong price, which
 * is the worst failure this screen has.
 * <p>
 * Every scan takes a token; an answer is shown only while its token is still the newest.
 * Not thread-safe by design: it is read and written on the JavaFX thread only, which is
 * where both the scan and the publishing of its answer happen.
 */
public final class PriceCheckSession {

    private long token;

    /** Starts a scan and returns the token its answer must carry to be shown. */
    public long begin() {
        return ++token;
    }

    /** Whether an answer carrying {@code scanToken} is still the one being waited for. */
    public boolean isCurrent(long scanToken) {
        return scanToken == token;
    }

    /**
     * Abandons whatever is in flight - the screen has gone back to waiting, so an answer
     * still on its way belongs to a customer who has already walked away.
     */
    public void cancel() {
        token++;
    }
}
