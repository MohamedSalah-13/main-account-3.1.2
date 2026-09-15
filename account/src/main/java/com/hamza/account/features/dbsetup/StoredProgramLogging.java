package com.hamza.account.features.dbsetup;

/**
 * Whether this MySQL server lets the application's own account create triggers.
 *
 * <p>The migrations create dozens of triggers and a few procedures, and the account the
 * setup tool creates holds privileges on its own schema only - deliberately no
 * {@code SUPER}. MySQL 8 enables the binary log by default, and with it on a trigger may
 * only be created by an account holding {@code SUPER} unless
 * {@code log_bin_trust_function_creators} is 1. So on a stock install the first start
 * of the program died at the first {@code CREATE TRIGGER} (error 1419), half-migrated,
 * while every step of this tool had reported success. It went unseen because the
 * development server had the variable persisted to 1 by hand, half an hour after the
 * tool was first committed.
 */
public enum StoredProgramLogging {

    /** The binary log is off; nothing stands in the way. */
    NOT_REQUIRED,
    /** The binary log is on and the server already trusts routine creators. */
    ALREADY_ALLOWED,
    /** The binary log is on and this run persisted {@code log_bin_trust_function_creators = 1}. */
    ENABLED,
    /** The binary log is on, trust is off, and the administrator could not change it. */
    BLOCKED,
    /** The binary log is on, trust is off, and the technician chose not to change it. */
    DECLINED;

    public static final String READ_SQL =
            "SELECT @@GLOBAL.log_bin, @@GLOBAL.log_bin_trust_function_creators";
    public static final String ENABLE_SQL = "SET PERSIST log_bin_trust_function_creators = 1";

    /** The state as read from the two server variables. */
    public static StoredProgramLogging of(boolean binaryLogEnabled, boolean trustFunctionCreators) {
        if (!binaryLogEnabled) {
            return NOT_REQUIRED;
        }
        return trustFunctionCreators ? ALREADY_ALLOWED : BLOCKED;
    }

    /** Whether the application's account will be refused when it creates a trigger. */
    public boolean blocksTriggers() {
        return this == BLOCKED || this == DECLINED;
    }
}
