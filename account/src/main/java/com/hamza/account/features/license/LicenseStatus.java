package com.hamza.account.features.license;

/**
 * What a server-issued licence file amounts to on this machine, today.
 *
 * <p>The split that matters is {@link #skipsTrial()}. Everything that does not is one
 * answer to the caller - "this machine is not licensed by this file" - and falls through to
 * whatever came before: the older licence, then the trial. <b>None of them is a failure to
 * be charged</b>: the trial's failure count allows one, permanently, and a licence file that
 * was cut short while being written is an accident rather than an attack.
 */
public enum LicenseStatus {

    /** No file in the server format was found. */
    ABSENT(false),
    /** A file claims the format and cannot be read as it. */
    MALFORMED(false),
    /** The text is well formed and the server's key did not sign it. */
    BAD_SIGNATURE(false),
    /** This build carries no server key yet, so it can accept no such licence. */
    SERVER_KEY_MISSING(false),
    /** This computer's identity could not be read, so nothing can be matched to it. */
    MACHINE_UNKNOWN(false),
    /** A genuine licence for another computer - how a copied program folder arrives. */
    OTHER_MACHINE(false),

    /** Licensed, and any subscription is running. */
    ACTIVE(true),
    /** A subscription has ended and its grace period has not. Everything still works. */
    GRACE(true),
    /**
     * A subscription and its grace period have both ended. The program opens, shows, prints,
     * exports and backs up; it records nothing new. <b>It still skips the trial</b>: sent
     * down the trial path, a customer of two years would meet "trial expired" - which is a
     * charged failure, and the end of the install.
     */
    READ_ONLY(true);

    private final boolean skipsTrial;

    LicenseStatus(boolean skipsTrial) {
        this.skipsTrial = skipsTrial;
    }

    public boolean skipsTrial() {
        return skipsTrial;
    }
}
