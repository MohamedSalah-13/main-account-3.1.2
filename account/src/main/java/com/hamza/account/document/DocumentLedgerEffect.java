package com.hamza.account.document;

import com.hamza.account.finance.MoneyMath;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * What one document does to its party's account and to the treasury.
 * <p>
 * Three numbers are stored on every document header - {@code total}, {@code discount}
 * and what it settled in cash ({@code paid_up}, {@code paid_from_treasury} or
 * {@code paid_to_treasury}, see {@link DocumentTableSpec#paid()}) - and two questions
 * are answered from them: how much the party now owes, and how much money moved. Both
 * answers used to be written inside the views that needed them, once per document
 * family, and the return families got them wrong.
 * <p>
 * <b>The rule, stated once.</b> The cash column is what the treasury moved, in the
 * direction {@link DocumentType#cashDirection()} declares. Whatever the cash column did
 * not cover ({@code net - paid}) is what went onto the party's account, in the direction
 * {@link DocumentType#ledgerSign()} declares. That is all of it, and it does not branch
 * on {@code invoice_type}: a cash document simply has {@code paid = net}, so its account
 * effect works out to zero on its own. {@code InvoicePaymentTerms.preview} is what
 * guarantees that - {@code paid} is forced to {@code net} for {@link
 * com.hamza.account.type.InvoiceType#CASH}.
 * <p>
 * <b>What the branch got wrong.</b> {@code account_customer_table} read
 * <pre>{@code IF(tsr.invoice_type = 1, tsr.total, 0)}</pre>
 * so a <em>deferred</em> sales return contributed nothing but its cash refund to the
 * customer's balance - a customer who returned 1000 of goods on account still owed the
 * 1000. {@code treasury_balance} read the mirror image
 * <pre>{@code IF(invoice_type = 1, paid_from_treasury, total - discount - paid_from_treasury)}</pre>
 * and paid that same 1000 out of a till that never opened. The two amounts were
 * swapped: the account's share was charged to the treasury and the treasury's share
 * credited to the account. Both are now {@link #ledgerPurchase()} and
 * {@link #treasuryOut()} here, and {@code PartyLedgerViewAcceptanceTest} holds the views
 * to them against a real database.
 * <p>
 * Deliberately unsigned on the way in and signed on the way out: the three inputs are
 * read straight off a stored row exactly as the table holds them - always positive, on
 * every document type - and every sign in the result comes from {@link DocumentType}.
 *
 * @param type     which document this is the effect of
 * @param total    the header's {@code total}, before its discount
 * @param discount the header's {@code discount}
 * @param paid     the header's cash column, whatever that family calls it
 */
public record DocumentLedgerEffect(
        DocumentType type,
        BigDecimal total,
        BigDecimal discount,
        BigDecimal paid) {

    public DocumentLedgerEffect {
        Objects.requireNonNull(type, "type");
        total = MoneyMath.money(total);
        discount = MoneyMath.money(discount);
        paid = MoneyMath.money(paid);
    }

    /** The same, from the {@code double}s the legacy models and result sets carry. */
    public static DocumentLedgerEffect of(DocumentType type,
                                          double total, double discount, double paid) {
        return new DocumentLedgerEffect(type, MoneyMath.decimal(total),
                MoneyMath.decimal(discount), MoneyMath.decimal(paid));
    }

    /** What the document came to after its discount. */
    public BigDecimal net() {
        return MoneyMath.subtract(total, discount);
    }

    /**
     * The part the cash column did not cover, which is what reaches the party's account.
     * Zero for a cash document, since {@code paid} is its whole net.
     */
    public BigDecimal onAccount() {
        return MoneyMath.subtract(net(), paid);
    }

    // ---- the party's account -------------------------------------------------------
    //
    // The three columns account_customer_table and account_suppliers_table union, in
    // their own words: a statement row carries purchase, discount and paid, and the
    // balance is purchase - discount - paid (PartyLedgerSpec.statementSql).

    /** The row's {@code purchase} column: what the party was charged, negative on a return. */
    public BigDecimal ledgerPurchase() {
        return signed(total);
    }

    /** The row's {@code discount} column. */
    public BigDecimal ledgerDiscount() {
        return signed(discount);
    }

    /**
     * The row's {@code paid} column. Negative on a return, which is the point: a refund
     * is a receipt going the other way, and the old views counted it as money the
     * customer had handed over.
     */
    public BigDecimal ledgerPaid() {
        return signed(paid);
    }

    /**
     * How much the party's outstanding balance moves - positive when they owe more.
     * The same arithmetic {@code PartyLedgerSpec.statementSql} does over the three
     * columns above, so the two cannot disagree.
     */
    public BigDecimal balanceChange() {
        return MoneyMath.subtract(
                MoneyMath.subtract(ledgerPurchase(), ledgerDiscount()), ledgerPaid());
    }

    // ---- the treasury ---------------------------------------------------------------

    /** What this document put into the treasury: its cash column, or nothing. */
    public BigDecimal treasuryIn() {
        return type.cashSign() > 0 ? paid : MoneyMath.ZERO;
    }

    /** What this document took out of the treasury: its cash column, or nothing. */
    public BigDecimal treasuryOut() {
        return type.cashSign() < 0 ? paid : MoneyMath.ZERO;
    }

    /** Positive when the treasury ends up with more money than it started with. */
    public BigDecimal treasuryChange() {
        return MoneyMath.subtract(treasuryIn(), treasuryOut());
    }

    private BigDecimal signed(BigDecimal amount) {
        return type.ledgerSign() < 0 ? MoneyMath.money(amount.negate()) : amount;
    }
}
