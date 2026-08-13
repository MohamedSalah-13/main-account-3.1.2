package com.hamza.controlsfx.error;

/** The three outcomes an exception boundary may present to the user. */
public enum ErrorCategory {
    /** The entered data is incomplete or invalid and may be corrected in the form. */
    VALIDATION,
    /** The request is valid, but a business rule refuses it in the current state. */
    BUSINESS,
    /** An unexpected application, database, file, or integration failure. */
    TECHNICAL
}
