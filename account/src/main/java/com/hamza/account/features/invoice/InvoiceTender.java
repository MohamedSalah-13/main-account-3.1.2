package com.hamza.account.features.invoice;

import com.hamza.account.finance.MoneyMath;
import com.hamza.account.type.InvoiceType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * What happens at the till when a sale is paid for: the amount due, what the customer handed
 * over, the change that goes back to them, and what is left on their account.
 * <p>
 * <b>What the customer hands over is not what the invoice records as paid.</b> A customer who
 * gives 100 for a sale of 42.50 has paid 42.50: the other 57.50 leaves the drawer again as
 * change, so the treasury moved by 42.50, and that is the only figure the document may carry -
 * the cash column is what the treasury moved (see {@code DocumentLedgerEffect}). So
 * {@link #paid()} is never more than {@link #due()}, and {@link #change()} is shown, never stored.
 * <p>
 * A cash invoice has to be settled in full. Short of the amount due it is {@link Problem#SHORT}:
 * the part not handed over would have to go somewhere, and a cash sale has no account to put it
 * on - that is what making the invoice deferred is for. A deferred invoice takes whatever is
 * handed over as a payment towards it, and the rest is {@link #remaining()} on the account.
 */
public record InvoiceTender(InvoiceType invoiceType, BigDecimal due, BigDecimal tendered,
                            BigDecimal paid, BigDecimal change, BigDecimal remaining) {

    /** Why an amount cannot settle this invoice, or {@link #NONE} when it can. */
    public enum Problem { NONE, NEGATIVE, SHORT }

    /**
     * The notes a customer here pays with. Each suggests the first round amount above what is due
     * that the note makes: for 42.50, a 5 suggests 45 and a 10 suggests 50.
     */
    private static final int[] NOTES = {5, 10, 20, 50, 100, 200};
    private static final int SUGGESTIONS = 4;

    public InvoiceTender {
        Objects.requireNonNull(invoiceType, "invoiceType");
        Objects.requireNonNull(due, "due");
        Objects.requireNonNull(tendered, "tendered");
        Objects.requireNonNull(paid, "paid");
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(remaining, "remaining");
    }

    /** Settles {@code terms} with {@code tendered} handed over; {@code null} is nothing handed over. */
    public static InvoiceTender of(InvoicePaymentTerms terms, BigDecimal tendered) {
        Objects.requireNonNull(terms, "terms");
        BigDecimal due = terms.netAmount();
        BigDecimal given = tendered == null ? MoneyMath.ZERO : MoneyMath.money(tendered);
        BigDecimal paid = given.signum() < 0 ? MoneyMath.ZERO : given.min(due);
        BigDecimal change = given.compareTo(due) > 0 ? MoneyMath.subtract(given, due) : MoneyMath.ZERO;
        return new InvoiceTender(terms.invoiceType(), due, given, MoneyMath.money(paid),
                change, MoneyMath.subtract(due, paid));
    }

    public Problem problem() {
        if (tendered.signum() < 0) {
            return Problem.NEGATIVE;
        }
        if (invoiceType == InvoiceType.CASH && tendered.compareTo(due) < 0) {
            return Problem.SHORT;
        }
        return Problem.NONE;
    }

    public boolean accepted() {
        return problem() == Problem.NONE;
    }

    /** What a short payment is missing; zero when nothing is. */
    public BigDecimal shortBy() {
        return tendered.signum() >= 0 && tendered.compareTo(due) < 0
                ? MoneyMath.subtract(due, tendered)
                : MoneyMath.ZERO;
    }

    /**
     * Round amounts a customer is likely to hand over for {@code due}, smallest first - the
     * next amount above it that each of {@link #NOTES} makes, without repeats. Nothing for an
     * amount that is not positive. The exact amount is not among them: it is always offered
     * on its own.
     */
    public static List<BigDecimal> suggestions(BigDecimal due) {
        if (due == null || due.signum() <= 0) {
            return List.of();
        }
        TreeSet<BigDecimal> amounts = new TreeSet<>();
        for (int note : NOTES) {
            BigDecimal size = BigDecimal.valueOf(note);
            BigDecimal rounded = due.divide(size, 0, RoundingMode.CEILING).multiply(size);
            if (rounded.compareTo(due) > 0) {
                amounts.add(MoneyMath.money(rounded));
            }
        }
        return amounts.stream().limit(SUGGESTIONS).toList();
    }
}
