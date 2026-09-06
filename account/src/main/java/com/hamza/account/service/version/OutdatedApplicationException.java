package com.hamza.account.service.version;

import com.hamza.controlsfx.error.ErrorCategory;
import com.hamza.controlsfx.error.UserFacingException;

/**
 * This copy of the program is older than the database it was pointed at.
 *
 * <p>Unchecked, because it is thrown out of the bootstrap task, whose {@code call()}
 * declares nothing - and user-facing, because the person who sees it can fix it in one
 * step: update this computer. A reference code and "an unexpected error occurred" would
 * send them to the wrong place entirely.
 */
public class OutdatedApplicationException extends RuntimeException implements UserFacingException {

    public OutdatedApplicationException(String message) {
        super(message);
    }

    @Override
    public ErrorCategory category() {
        return ErrorCategory.BUSINESS;
    }

    @Override
    public String userMessage() {
        return getMessage();
    }
}
