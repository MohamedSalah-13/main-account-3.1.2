package com.hamza.account.features.party.statement;

/**
 * What one row of a party statement is, as a number rather than as a sentence.
 * <p>
 * The code is the {@code information} column of {@code account_customer_table} and
 * {@code account_suppliers_table} — 1 for the opening balance, 2 for a payment, 3 for an
 * invoice, 4 for a return — and the four have been those numbers since {@code V1}.
 * <p>
 * <b>Why this exists next to {@link com.hamza.account.type.TableName}, which carries the
 * same four ids.</b> That enum holds a {@code StringProperty} that re-reads itself from
 * {@link com.hamza.controlsfx.language.LanguageManager} whenever the locale changes, so
 * it cannot leave the UI layer: it drags JavaFX into anything that names it, and this
 * package has none by design. The screen translates a kind for display through
 * {@link #messageKey()}; nothing compares a translated label to decide anything.
 * <p>
 * That last sentence is the whole point. {@code AccountDetailsWithItemsController}
 * decided whether a row could be expanded into its invoice lines by comparing its
 * {@code information} text against the Arabic literals {@code "المبيعات"} and
 * {@code "مرتجع المبيعات"} — the same mistake {@code MovementLabel} records on the
 * treasury side, where translating one side of the comparison silently emptied every
 * filter. A row's kind is a number here, and a number does not get translated.
 */
public enum PartyMovementKind {

    /** The opening balance carried on the party's own row. One per party, undated. */
    OPENING(1, "party.movement.opening"),

    /** A payment: a collection from a customer, or a payment to a supplier. */
    PAYMENT(2, "party.movement.payment"),

    /** A sales invoice for a customer, a purchase invoice for a supplier. */
    INVOICE(3, "party.movement.invoice"),

    /** A return of either, which moves the account the other way. */
    RETURN(4, "party.movement.return");

    private final int code;
    private final String messageKey;

    PartyMovementKind(int code, String messageKey) {
        this.code = code;
        this.messageKey = messageKey;
    }

    public int code() {
        return code;
    }

    /** The key the screen translates for display. Never compared against anything. */
    public String messageKey() {
        return messageKey;
    }

    /**
     * The kind carrying this {@code information} code.
     *
     * @throws IllegalArgumentException if no kind carries it — the value is in the
     *                                 message, because a bare failure inside a row
     *                                 mapper names nothing findable in a table of
     *                                 thousands of movements. The same reasoning as
     *                                 {@code TableName.requireById}.
     */
    public static PartyMovementKind fromCode(int code) {
        for (PartyMovementKind kind : values()) {
            if (kind.code == code) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown party movement information code: " + code);
    }

    /**
     * Whether a row of this kind can be opened to show the lines of its document.
     * <p>
     * Only the two document kinds have lines; an opening balance and a payment have
     * nothing underneath them. This replaces the comparison against translated labels
     * described in this enum's own javadoc.
     */
    public boolean hasDocumentLines() {
        return this == INVOICE || this == RETURN;
    }
}
