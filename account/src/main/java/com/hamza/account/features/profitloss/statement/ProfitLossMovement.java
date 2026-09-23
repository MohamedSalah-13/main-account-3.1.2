package com.hamza.account.features.profitloss.statement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One thing a row of the statement is made of: a sales invoice, a sales return or an expense, with what
 * it did to each column. A return's sales and cost are negative, as {@code document_profit} signs them, so
 * the movements of a row add up to the row.
 */
public record ProfitLossMovement(Kind kind, long number, LocalDate date, String name, String note,
                                 BigDecimal netSales, BigDecimal cost, BigDecimal expense) {

    public enum Kind {
        SALE("profitloss.movement.sale"),
        SALE_RETURN("profitloss.movement.sale.return"),
        EXPENSE("profitloss.movement.expense");

        private final String messageKey;

        Kind(String messageKey) {
            this.messageKey = messageKey;
        }

        public String messageKey() {
            return messageKey;
        }
    }

    public ProfitLossMovement {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(date, "date");
        name = Objects.requireNonNullElse(name, "");
        note = Objects.requireNonNullElse(note, "");
        netSales = netSales == null ? BigDecimal.ZERO : netSales;
        cost = cost == null ? BigDecimal.ZERO : cost;
        expense = expense == null ? BigDecimal.ZERO : expense;
    }

    /** What the movement added to the net profit. */
    public BigDecimal profit() {
        return netSales.subtract(cost).subtract(expense);
    }
}
