package com.hamza.controlsfx.excel;

public class ExcelException extends Exception {

    /**
     * Constructs a new ExcelException with null as its detail message.
     */
    public ExcelException() {
    }

    /**
     * Constructs a new ExcelException with the specified detail message.
     *
     * @param message The detail message which provides more information about the exception.
     */
    public ExcelException(String message) {
        super(message);
    }

    /**
     * Constructs a new ExcelException with the specified detail message and cause.
     *
     * @param message the detail message (which is saved for later retrieval by the Throwable.getMessage() method).
     * @param cause the cause (which is saved for later retrieval by the Throwable.getCause() method). (A null value is permitted, and indicates that the cause is nonexistent or unknown
     * .)
     */
    public ExcelException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new ExcelException with the specified cause.
     *
     * @param cause the cause of this exception. A null value is permitted and
     *        indicates that the cause is nonexistent or unknown.
     */
    public ExcelException(Throwable cause) {
        super(cause);
    }
}
