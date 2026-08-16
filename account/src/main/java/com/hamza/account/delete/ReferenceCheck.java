package com.hamza.account.delete;

import java.util.regex.Pattern;

/**
 * "count the rows of {@code table} whose {@code column} is this id, and call them
 * {@code labelKey} - an i18n bundle key - when you report them".
 * <p>
 * Declare one for every foreign key that would refuse the delete - and only
 * those. A check that does not mirror a real constraint would refuse a delete the
 * database was happy to make; a constraint with no check still refuses, only with
 * the general message instead of a count. Keys declared {@code ON DELETE CASCADE}
 * are deliberately not declared here: those rows go with the parent, so counting
 * them would block a delete that is meant to succeed.
 */
public record ReferenceCheck(String table, String column, String labelKey) {

    /**
     * Identifiers reach the SQL by concatenation - they name schema objects, so
     * they cannot be bound as parameters. They come from {@link DeleteRegistry}
     * and never from input, and this keeps it that way: a typo that would produce
     * anything other than a plain identifier fails at construction rather than
     * reaching the database.
     */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public ReferenceCheck {
        if (!IDENTIFIER.matcher(table).matches()) {
            throw new IllegalArgumentException("Not a table name: " + table);
        }
        if (!IDENTIFIER.matcher(column).matches()) {
            throw new IllegalArgumentException("Not a column name: " + column);
        }
    }
}
