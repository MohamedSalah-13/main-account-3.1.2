package com.hamza.account.period;

import com.hamza.controlsfx.language.LanguageManager;

import java.util.regex.Pattern;

/**
 * A kind of dated document the period lock applies to, and where to find its date.
 * <p>
 * Declared rather than written into each service for the reason {@code DeleteRegistry}
 * and {@code OpeningBalanceRegistry} are: the same three lines were going to appear in
 * seven services, and the seventh copy is the one that reads the wrong column.
 *
 * @param labelKey    the i18n bundle key for what it is, for the refusal: "period.doc.sales.invoice"
 * @param table       where the row lives
 * @param idColumn    its primary key - not always {@code id}: the invoice tables are
 *                    keyed by {@code invoice_number}, and the account tables by
 *                    {@code account_num}
 * @param dateColumn  the date the document belongs to, which is the business date and
 *                    not {@code date_insert}. A sale entered today for yesterday
 *                    belongs to yesterday, and that is the date a close is about
 */
public record LockedDocument(String labelKey, String table, String idColumn, String dateColumn) {

    /** As in {@code ReferenceCheck}: these are concatenated into SQL, so they are checked. */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public LockedDocument {
        for (String identifier : new String[]{table, idColumn, dateColumn}) {
            if (!IDENTIFIER.matcher(identifier).matches()) {
                throw new IllegalArgumentException("Not an identifier: " + identifier);
            }
        }
    }

    /** What it is, for the refusal - resolved through the current language, not baked in at registration. */
    public String label() {
        return LanguageManager.getInstance().getString(labelKey);
    }
}
