package com.hamza.account.features.party.statement;

import com.hamza.account.finance.MoneyMath;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One line of a party's account statement.
 * <p>
 * A plain record with no JavaFX: the screen wraps it, the way
 * {@code docs/new-code-rules.md} §5 rule 2 asks, and {@code BaseAccount} — which is on
 * the {@code ModelPurityArchitectureTest} debt list — is not what travels here.
 * <p>
 * <b>{@code debit} and {@code credit} are presentation, {@code balanceChange} is the
 * arithmetic.</b> The stored row carries {@code purchase}, {@code discount} and
 * {@code paid}, and the account moves by {@code purchase - discount - paid} — the rule
 * {@link com.hamza.account.document.DocumentLedgerEffect#balanceChange()} states once.
 * A return carries all three negative since {@code V15__return_cash_split.sql}, so its
 * change is negative without anything here branching on the document type. The two
 * columns a person reads are derived from that one signed number by {@link #debit()} and
 * {@link #credit()}, so they cannot disagree with it.
 *
 * @param movementId    the row's own key: {@code account_num} for a payment, the invoice
 *                      number for a document, {@code 0} for the opening balance
 * @param date          the movement's own date, which is what a statement is ordered and
 *                      filtered by
 * @param enteredAt     when the row was entered. The tie-breaker inside one day, and
 *                      null on no branch of the view — but read as nullable anyway,
 *                      because the mapper that came before this one threw on a null here
 * @param kind          what this row is, as a number. See {@link PartyMovementKind}
 * @param cash          whether the document was settled in cash, for the deferred-only
 *                      filter. Meaningless for an opening balance
 * @param reference      the document or movement number shown to the user
 * @param purchase      the row's {@code purchase} column, signed as stored
 * @param discount      the row's {@code discount} column, signed as stored
 * @param paid          the row's {@code paid} column, signed as stored
 * @param runningBalance what the party owed after this row, computed in SQL over every
 *                      row up to it — including the ones a filter hid, since a running
 *                      balance that skips rows is not a balance
 * @param treasuryId    which till the cash side touched, or 0
 * @param treasuryName  that till's name, or empty
 * @param userId        who entered the row, or 0 where the view cannot say
 * @param userName      that user's name, or empty
 * @param notes         the row's notes
 */
public record PartyStatementRow(
        long movementId,
        LocalDate date,
        LocalDateTime enteredAt,
        PartyMovementKind kind,
        boolean cash,
        long reference,
        BigDecimal purchase,
        BigDecimal discount,
        BigDecimal paid,
        BigDecimal runningBalance,
        int treasuryId,
        String treasuryName,
        int userId,
        String userName,
        String notes) {

    public PartyStatementRow {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(kind, "kind");
        purchase = MoneyMath.money(purchase == null ? BigDecimal.ZERO : purchase);
        discount = MoneyMath.money(discount == null ? BigDecimal.ZERO : discount);
        paid = MoneyMath.money(paid == null ? BigDecimal.ZERO : paid);
        runningBalance = MoneyMath.money(runningBalance == null ? BigDecimal.ZERO : runningBalance);
        treasuryName = treasuryName == null ? "" : treasuryName;
        userName = userName == null ? "" : userName;
        notes = notes == null ? "" : notes;
    }

    /**
     * How much this row moved the party's balance — positive when they owe more.
     * <p>
     * The same arithmetic as {@code DocumentLedgerEffect.balanceChange()} and as
     * {@code PartyLedgerSpec.statementSql()}'s {@code amount} column, so the three cannot
     * drift apart. {@code PartyStatementAgreesWithLedgerEffectTest} is what holds them
     * together.
     */
    public BigDecimal balanceChange() {
        return MoneyMath.subtract(MoneyMath.subtract(purchase, discount), paid);
    }

    /** The row after its discount: what the document came to, or what was paid. */
    public BigDecimal net() {
        return MoneyMath.subtract(purchase, discount);
    }

    /**
     * The debtor column, unsigned.
     * <p>
     * Two things land in it: what the party was charged ({@link #net()} when positive),
     * and cash handed back to them (a negative {@code paid}, which is a refund). So a
     * cash sales return of 1000 reads as a credit of 1000 for the goods and a debit of
     * 1000 for the money returned, netting to nothing — which is what happened.
     * <p>
     * <b>This is deliberately not {@code max(balanceChange, 0)}.</b> That would collapse
     * a cash invoice to two zeros and make it vanish from the statement, since its
     * {@code paid} covers its whole net. A reader needs to see the invoice's value and
     * what was paid against it on the same line; the two columns still subtract to
     * {@link #balanceChange()}, which {@code PartyStatementTest} pins for every kind.
     */
    public BigDecimal debit() {
        return MoneyMath.add(positive(net()), positive(paid.negate()));
    }

    /** The creditor column, unsigned: what the party paid, and what they were credited. */
    public BigDecimal credit() {
        return MoneyMath.add(positive(paid), positive(net().negate()));
    }

    private static BigDecimal positive(BigDecimal value) {
        return value.signum() > 0 ? value : MoneyMath.ZERO;
    }

    /** Whether this row can be opened to show the lines of its document. */
    public boolean hasDocumentLines() {
        return kind.hasDocumentLines();
    }
}
