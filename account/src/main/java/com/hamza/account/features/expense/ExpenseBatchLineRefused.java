package com.hamza.account.features.expense;

import com.hamza.controlsfx.error.UserValidationException;

/**
 * One line of a batch was refused, so the whole batch was.
 * <p>
 * It carries the line number and the refusal as it was thrown - a key or a sentence, never formatted
 * here. The screen that holds the batch puts the two together, because a service does not build text
 * for a person to read (docs/new-code-rules.md ق-ل6).
 */
public final class ExpenseBatchLineRefused extends UserValidationException {

    private final int line;

    public ExpenseBatchLineRefused(int line, String refusal, Throwable cause) {
        super(refusal, cause);
        this.line = line;
    }

    /** One-based, as the screen numbers its rows. */
    public int line() {
        return line;
    }
}
