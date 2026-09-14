package com.hamza.account.features.backup;

import java.util.Locale;

/**
 * Why a backup was taken - which is what decides how long it is kept.
 *
 * <p>Every backup used to be {@code backup_<time>.enc}, and retention kept the newest thirty
 * {@code .enc} files in the folder whatever they were. So the kinds pushed each other out: with
 * "back up after every invoice" on, the thirty were the last thirty invoices - perhaps an hour of
 * trading - and the scheduled copies from yesterday were gone, together with the copy taken
 * <em>before a wipe</em> or <em>before invoices were deleted</em>, the one somebody reaches for
 * the day after. A backup kept for an hour is not a backup of yesterday.
 *
 * <p>Each kind now has a prefix of its own and is pruned against its own kind only. The
 * prefix is also what stops retention deleting files that are not ours: the folder defaults to
 * the user's home directory, and matching on {@code .enc} alone deleted any encrypted file there.
 */
public enum BackupKind {

    /**
     * The timer, the backup button and the copy on closing. Keeps the name every backup
     * already had, so the files an install holds today stay in this pool and keep their place.
     */
    SCHEDULED("backup_", 30),

    /** Taken after invoices are saved. Frequent by nature, so it rolls quickly. */
    AFTER_INVOICE("after-invoice_", 10),

    /** Taken before deleting documents or wiping data - the copy of what was about to go. */
    BEFORE_DELETE("before-delete_", 30),

    /**
     * Taken by a restore before it replaces the database. Rare, and the only copy of what the
     * restore overwrote, so nothing prunes it: {@link #keep()} is {@link #KEEP_ALL}.
     */
    BEFORE_RESTORE("before-restore_", 0);

    /** {@link #keep()} for a kind retention never deletes. */
    public static final int KEEP_ALL = 0;

    public static final String SUFFIX = ".enc";

    private final String prefix;
    private final int keep;

    BackupKind(String prefix, int keep) {
        this.prefix = prefix;
        this.keep = keep;
    }

    public String prefix() {
        return prefix;
    }

    /** How many of this kind to keep, newest first; {@link #KEEP_ALL} for all of them. */
    public int keep() {
        return keep;
    }

    public String fileName(String timestamp) {
        return prefix + timestamp + SUFFIX;
    }

    /** Whether {@code fileName} is a backup of this kind. The suffix is matched without case. */
    public boolean matches(String fileName) {
        return fileName != null
                && fileName.startsWith(prefix)
                && fileName.toLowerCase(Locale.ROOT).endsWith(SUFFIX);
    }
}
