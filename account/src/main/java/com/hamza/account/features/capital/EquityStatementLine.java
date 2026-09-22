package com.hamza.account.features.capital;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * One line of the equity statement as it is shown and printed, in reading order.
 *
 * <p>The order and the signs are decided here, not in the screen, so the table on screen, the PDF
 * and the spreadsheet cannot say the statement three ways. A {@link Kind#DETAIL} line explains the
 * line above it and is not added again; a {@link Kind#TOTAL} line is the sum of the lines since the
 * previous total, and {@code EquityStatementLineTest} holds each total to that.</p>
 *
 * @param labelKey the bundle key the screen translates - one whole literal each, so the message-key
 *                 test can see them
 * @param amount   signed as it acts on equity: drawings are negative
 */
public record EquityStatementLine(String labelKey, BigDecimal amount, Kind kind) {

    public enum Kind {
        /** A figure that moves equity. */
        LINE,
        /** A part of the line above it, shown for the reader and not added again. */
        DETAIL,
        /** Where equity stands. */
        TOTAL
    }

    public static List<EquityStatementLine> of(EquityStatement statement) {
        BroughtForward forward = statement.broughtForward();
        List<EquityStatementLine> lines = new ArrayList<>();
        lines.add(new EquityStatementLine("capital.equity.brought.forward", forward.total(), Kind.LINE));
        lines.add(new EquityStatementLine("capital.equity.brought.forward.treasuries", forward.treasuries(), Kind.DETAIL));
        lines.add(new EquityStatementLine("capital.equity.brought.forward.customers", forward.customers(), Kind.DETAIL));
        lines.add(new EquityStatementLine("capital.equity.brought.forward.suppliers", forward.suppliers().negate(), Kind.DETAIL));
        lines.add(new EquityStatementLine("capital.equity.brought.forward.stock", forward.stock(), Kind.DETAIL));
        lines.add(new EquityStatementLine("capital.equity.capital.before", statement.capitalBefore().net(), Kind.LINE));
        lines.add(new EquityStatementLine("capital.equity.profit.before", statement.profitBefore(), Kind.LINE));
        lines.add(new EquityStatementLine("capital.equity.opening", statement.opening(), Kind.TOTAL));
        lines.add(new EquityStatementLine("capital.equity.paid.in", statement.paidIn(), Kind.LINE));
        lines.add(new EquityStatementLine("capital.equity.drawn", statement.drawn().negate(), Kind.LINE));
        lines.add(new EquityStatementLine("capital.equity.profit", statement.profit(), Kind.LINE));
        lines.add(new EquityStatementLine("capital.equity.closing", statement.closing(), Kind.TOTAL));
        return List.copyOf(lines);
    }
}
