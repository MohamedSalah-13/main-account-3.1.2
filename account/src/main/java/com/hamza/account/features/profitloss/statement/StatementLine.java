package com.hamza.account.features.profitloss.statement;

import com.hamza.account.features.profitloss.ProfitLossFigures;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * One line of the profit and loss statement as it is read down the page.
 *
 * <p><b>Every amount is signed the way it moves the result</b>: a discount, a return, a cost and an
 * expense are negative, so each section's subtotal is the sum of the lines above it and each result the
 * sum of the subtotals before it. A reader can check the page with nothing but addition.</p>
 *
 * @param kind       how the line is drawn
 * @param messageKey the caption's key, or null when {@code name} is the caption (an expense heading)
 * @param name       a caption that is data rather than a key, or null
 * @param current    the period's amount; null on a heading, which carries no figure
 * @param previous   the compared period's amount; null on a heading
 */
public record StatementLine(Kind kind, String messageKey, String name, BigDecimal current, BigDecimal previous) {

    public enum Kind {
        /** A section's title: revenue, cost, expenses, and what is outside the profit. */
        HEADING,
        /** A figure inside a section. */
        ITEM,
        /** The figure a section adds up to. */
        SUBTOTAL,
        /** The gross and net profit: the subtotals before them, added. */
        RESULT
    }

    public StatementLine {
        Objects.requireNonNull(kind, "kind");
        if (messageKey == null && name == null) {
            throw new IllegalArgumentException("A line needs a caption");
        }
        if (kind != Kind.HEADING && (current == null || previous == null)) {
            throw new IllegalArgumentException("Only a heading carries no figure");
        }
    }

    static StatementLine heading(String messageKey) {
        return new StatementLine(Kind.HEADING, messageKey, null, null, null);
    }

    static StatementLine item(String messageKey, BigDecimal current, BigDecimal previous) {
        return new StatementLine(Kind.ITEM, messageKey, null, current, previous);
    }

    static StatementLine named(String name, BigDecimal current, BigDecimal previous) {
        return new StatementLine(Kind.ITEM, null, name, current, previous);
    }

    static StatementLine subtotal(String messageKey, BigDecimal current, BigDecimal previous) {
        return new StatementLine(Kind.SUBTOTAL, messageKey, null, current, previous);
    }

    static StatementLine result(String messageKey, BigDecimal current, BigDecimal previous) {
        return new StatementLine(Kind.RESULT, messageKey, null, current, previous);
    }

    public boolean isHeading() {
        return kind == Kind.HEADING;
    }

    /**
     * How the line moved against the compared period; absent on a heading or against a zero.
     *
     * <p>A result is compared signed, so a loss that shrinks reads as a rise. Every other line is compared
     * by its size: an expense is written negative, and expenses of 4,400 against 4,000 are ten percent
     * <b>more</b>, which a signed comparison would print as "-10%" beside a figure that grew. Where the
     * two periods have opposite signs there is no size to compare and the signed change is the answer.</p>
     */
    public Optional<BigDecimal> change() {
        if (isHeading()) {
            return Optional.empty();
        }
        if (kind == Kind.RESULT || current.signum() * previous.signum() < 0) {
            return ProfitLossFigures.change(current, previous);
        }
        return ProfitLossFigures.change(current.abs(), previous.abs());
    }
}
