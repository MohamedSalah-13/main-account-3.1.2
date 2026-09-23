package com.hamza.account.features.report.monthly;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * What a day's, a month's or a year's documents of one side came to, in the pieces the net is made of.
 *
 * <p>{@link #net()} is {@code gross - discount - returns}, where the returns are already net of their own
 * discounts: {@code total - discount} over the invoices less the same over the returns. For the sales it
 * is the profit and loss's net sales, the figure {@code document_profit} reads, and for the purchases the
 * yearly report's net purchases - one arithmetic for both, so this report never says a third thing.</p>
 *
 * @param invoices        how many invoices
 * @param gross           the invoices' totals before their own discounts
 * @param discount        the invoices' own discounts
 * @param returnDocuments how many returns
 * @param returnsGross    the returns' totals before their own discounts
 * @param returnsDiscount the returns' own discounts
 */
public record MonthFigures(int invoices, BigDecimal gross, BigDecimal discount,
                           int returnDocuments, BigDecimal returnsGross, BigDecimal returnsDiscount) {

    public static final MonthFigures ZERO =
            new MonthFigures(0, BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO);

    public MonthFigures {
        gross = Objects.requireNonNullElse(gross, BigDecimal.ZERO);
        discount = Objects.requireNonNullElse(discount, BigDecimal.ZERO);
        returnsGross = Objects.requireNonNullElse(returnsGross, BigDecimal.ZERO);
        returnsDiscount = Objects.requireNonNullElse(returnsDiscount, BigDecimal.ZERO);
    }

    /** What came back, net of what those returns were discounted - what was actually given back. */
    public BigDecimal returns() {
        return returnsGross.subtract(returnsDiscount);
    }

    /** The invoices less their discounts, less what came back. */
    public BigDecimal net() {
        return gross.subtract(discount).subtract(returns());
    }

    public MonthFigures plus(MonthFigures other) {
        return new MonthFigures(invoices + other.invoices, gross.add(other.gross), discount.add(other.discount),
                returnDocuments + other.returnDocuments, returnsGross.add(other.returnsGross),
                returnsDiscount.add(other.returnsDiscount));
    }

    /** Nothing was written: no invoice and no return, whatever the amounts would add to. */
    public boolean isEmpty() {
        return invoices == 0 && returnDocuments == 0;
    }
}
