package com.hamza.account.features.party.payment;

/**
 * What a hand-entered movement on a party's account is.
 * <p>
 * Three kinds, and the difference between the first and the other two is not cosmetic: a
 * collection is money arriving in a till, and a note is a decision about what someone owes.
 * They are written to different columns, guarded by different permissions, and only one of them
 * belongs to a shift.
 * <p>
 * <b>{@code paid} is cash and {@code purchase} is not, and that is a fact about the schema
 * rather than a convention here.</b> {@code treasury_balance} unions
 * {@code customers_accounts.paid} as money into the till and {@code suppliers_accounts.paid} as
 * money out of it; neither view reads {@code purchase} at all. So a note written through
 * {@code purchase} moves the party's account and leaves every treasury balance exactly where it
 * was — which is what a note means — while a note written through {@code paid} would quietly
 * claim that cash had changed hands. That is why a credit note is a negative {@code purchase}
 * and not a positive {@code paid}.
 * <p>
 * <b>And this is the way out of a trap that had no exit.</b> {@code OpeningBalanceGuard} refuses
 * to rewrite {@code first_balance} once a party has moved — correctly: it is the one figure with
 * no date on it, so changing it changes what the party owed on every earlier day, and a statement
 * signed last month would print differently today. The message it shows says to record a movement
 * on the account instead. Until {@code V55} the only movement anyone could record was a
 * collection, so an opening balance entered too low could never be corrected and the message
 * pointed at a road that did not exist. A debit note raises it; a credit note lowers it; both are
 * dated, both are on the statement, and both say who entered them.
 */
public enum PartyEntryKind {

    /** Money received from a customer, or paid to a supplier. Goes to {@code paid}. */
    COLLECTION("party.payment.kind.collection", true, +1),

    /** The party owes more, with nothing on the other side. A positive {@code purchase}. */
    DEBIT_NOTE("party.payment.kind.debit.note", false, +1),

    /** The party owes less, with nothing on the other side. A negative {@code purchase}. */
    CREDIT_NOTE("party.payment.kind.credit.note", false, -1);

    private final String messageKey;
    private final boolean movesCash;
    private final int sign;

    PartyEntryKind(String messageKey, boolean movesCash, int sign) {
        this.messageKey = messageKey;
        this.movesCash = movesCash;
        this.sign = sign;
    }

    /** The key the screen translates. Never compared against anything - see {@code MovementLabel}. */
    public String messageKey() {
        return messageKey;
    }

    /**
     * Whether this movement passes through a till.
     * <p>
     * What hangs off it: the treasury picker, the wallet fee, the shift gate and the shift's cash
     * journal. A note needs none of them, and requiring an open shift for one would stop a
     * correction being made outside trading hours — which is when corrections get made.
     */
    public boolean movesCash() {
        return movesCash;
    }

    /** Which way a note moves the balance. Meaningless for a collection, which is always positive. */
    public int sign() {
        return sign;
    }

    /** What goes in the {@code paid} column: the amount for a collection, nothing for a note. */
    public double paidColumn(double amount) {
        return movesCash ? amount : 0;
    }

    /** And in {@code purchase}: the signed amount for a note, nothing for a collection. */
    public double purchaseColumn(double amount) {
        return movesCash ? 0 : sign * amount;
    }

    /** Whether a movement of this kind may be put against a particular invoice. */
    public boolean allowsAllocation() {
        return this == COLLECTION;
    }
}
