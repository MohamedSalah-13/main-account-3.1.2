package com.hamza.controlsfx.error;

/**
 * Marks an expected failure whose text was deliberately written for an end user.
 * <p>
 * Merely giving an exception an Arabic message does not make it safe to display:
 * JDBC and library messages can contain SQL, paths, and implementation details.
 * Exception boundaries expose a message only when the exception implements this
 * contract; every other throwable is treated as technical.
 */
public interface UserFacingException {

    ErrorCategory category();

    String userMessage();
}
