package com.hamza.account.features.dbsetup;

/**
 * A first install that did not finish, and the step it stopped at.
 * <p>
 * The step is a name, not a sentence: the reader is the installer, which shows its own message
 * in its own language and points at the log. Nothing here is translated, and nothing here ever
 * carries a password - the cause is logged by the caller, the step goes in the result file.
 */
public final class LocalProvisioningException extends Exception {

    public enum Step {
        NO_MYSQLD, NO_FREE_PORT, WRITE_INI, INITIALIZE, START, SECURE_ROOT, CREATE_ACCOUNT,
        SAVE_ROOT_SECRET, SAVE_CONFIG, SHUTDOWN
    }

    private final Step step;

    public LocalProvisioningException(Step step, Throwable cause) {
        super(step.name(), cause);
        this.step = step;
    }

    public Step step() {
        return step;
    }
}
