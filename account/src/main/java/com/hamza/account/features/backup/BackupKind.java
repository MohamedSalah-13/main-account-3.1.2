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
     * <p>
     * Tiered rather than a count, because this is the kind somebody reaches back through: the
     * newest 24, then one a day for a week, one a week for four weeks and one a month for a year
     * - at most 47 files, where it used to be 30 that covered barely more than a day on an
     * hourly schedule. See {@link RetentionPolicy.Tiered}.
     */
    SCHEDULED("backup_", new RetentionPolicy.Tiered(24, 7, 4, 12)),

    /** Taken after invoices are saved. Frequent by nature, so it rolls quickly. */
    AFTER_INVOICE("after-invoice_", new RetentionPolicy.KeepNewest(10)),

    /** Taken before deleting documents or wiping data - the copy of what was about to go. */
    BEFORE_DELETE("before-delete_", new RetentionPolicy.KeepNewest(30)),

    /**
     * Taken by a restore before it replaces the database. Rare, and the only copy of what the
     * restore overwrote, so nothing prunes it.
     */
    BEFORE_RESTORE("before-restore_", new RetentionPolicy.KeepAll());

    public static final String SUFFIX = ".enc";

    private final String prefix;
    private final RetentionPolicy retention;

    BackupKind(String prefix, RetentionPolicy retention) {
        this.prefix = prefix;
        this.retention = retention;
    }

    public String prefix() {
        return prefix;
    }

    /** Which of this kind's backups are kept. */
    public RetentionPolicy retention() {
        return retention;
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
