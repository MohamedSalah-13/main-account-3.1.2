package com.hamza.controlsfx.database;

public class DaoException extends Exception {

    /**
     * Constructs a new DaoException with {@code null} as its detail message.
     * The cause is not initialized, and may subsequently be initialized
     * by a call to {@link #initCause}.
     */
    public DaoException() {
        super();
    }

    /**
     * Constructs a new DaoException with the specified detail message.
     *
     * @param message the*/
    public DaoException(String message) {
        super(message);
    }

    /**
     * Constructs a new DaoException with the specified detail message and cause.
     *
     * @param message the detail message.
     * @param cause the cause of the exception.
     */
    public DaoException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new DaoException with the specified cause.
     *
     * @param cause the cause of the exception, which can be retrieved later using the {@link Throwable#getCause()} method.
     */
    public DaoException(Throwable cause) {
        super(cause);
    }
}
