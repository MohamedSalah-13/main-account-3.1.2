package com.hamza.account.features.expense.report;

import com.hamza.account.features.expense.ExpenseFilter;

import java.time.LocalDate;
import java.util.Optional;

/**
 * What the expenses are totalled by in the report by dimension - one report with a selector, where there
 * would otherwise be five reports repeating one statement (docs/expenses-plan.md §4, item 4).
 * <p>
 * <b>Only identifiers this enum owns enter the SQL</b>, the rule {@code MasterDataKind} keeps: the key and
 * label expressions are constants here, and nothing a person types reaches them.
 */
public enum ExpenseDimension {

    TREASURY("expense.report.dimension.treasury", "d.treasury_id", "MAX(t.t_name)"),
    USER("expense.report.dimension.user", "d.user_id", "MAX(u.user_name)"),
    SHIFT("expense.report.dimension.shift", "d.shift_id", "NULL"),
    PAYEE("expense.report.dimension.payee", "d.payee", "MAX(d.payee)"),
    /** {@code yyyymm} - a number, so the months sort as months, and the label is written in Java. */
    MONTH("expense.report.dimension.month", "YEAR(d.date) * 100 + MONTH(d.date)", "NULL");

    private final String messageKey;
    private final String keySql;
    private final String labelSql;

    ExpenseDimension(String messageKey, String keySql, String labelSql) {
        this.messageKey = messageKey;
        this.keySql = keySql;
        this.labelSql = labelSql;
    }

    /** The key the screen translates. Never compared against anything. */
    public String messageKey() {
        return messageKey;
    }

    String keySql() {
        return keySql;
    }

    String labelSql() {
        return labelSql;
    }

    /** Months in the order of the calendar; everything else by what it came to, the largest first. */
    public boolean sortsByKey() {
        return this == MONTH;
    }

    /**
     * What a line is called when the query read no name beside its key: a shift is its number, a month is
     * written {@code 2026-09}. {@code null} when the line has no value at all - the screen writes that.
     */
    public String label(String key, String readLabel) {
        if (key == null) {
            return null;
        }
        if (this == MONTH) {
            int yearMonth = Integer.parseInt(key);
            return "%d-%02d".formatted(yearMonth / 100, yearMonth % 100);
        }
        return readLabel == null || readLabel.isBlank() ? key : readLabel;
    }

    /**
     * The list's filter for one line of this report, or empty when the list has no condition that says
     * exactly that line.
     * <p>
     * A shift and a payee have none: the list's text search is a <i>contains</i> match over six columns, so
     * opening it on "محمد" would also bring "محمد علي" and every note mentioning him - a list that does not
     * add up to the line it was opened from. No action is better than one that answers another question.
     *
     * @param key the line's key as the query answered it, or {@code null} for the line with no value
     */
    public Optional<ExpenseFilter> narrow(ExpenseFilter scope, String key) {
        if (key == null) {
            return Optional.empty();
        }
        return switch (this) {
            case TREASURY -> Optional.of(scope.withTreasury(Integer.valueOf(key)));
            case USER -> Optional.of(scope.withUser(Integer.valueOf(key)));
            case MONTH -> {
                int yearMonth = Integer.parseInt(key);
                LocalDate first = LocalDate.of(yearMonth / 100, yearMonth % 100, 1);
                yield Optional.of(ExpensePeriods.within(scope, first, first.plusMonths(1).minusDays(1)));
            }
            case SHIFT, PAYEE -> Optional.empty();
        };
    }

    /** What the line with no value is called - an expense entered outside any shift, or naming no payee. */
    public String emptyLabelKey() {
        return switch (this) {
            case SHIFT -> "expense.report.dimension.no.shift";
            case PAYEE -> "expense.report.dimension.no.payee";
            default -> "expense.report.dimension.none";
        };
    }
}
