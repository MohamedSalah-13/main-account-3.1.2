package com.hamza.account.features.expense;

import java.util.List;

/**
 * One page of expenses with the figures for the whole filtered set.
 *
 * @param truncated only ever true for a print extract that hit its ceiling - the screen says so rather
 *                  than handing over a file that silently stops
 */
public record ExpensePage(List<ExpenseRow> rows, ExpenseSummary summary, int page,
                          boolean hasPrevious, boolean hasNext, boolean truncated) {

    public static final ExpensePage EMPTY =
            new ExpensePage(List.of(), ExpenseSummary.EMPTY, 0, false, false, false);

    public ExpensePage {
        rows = List.copyOf(rows);
    }
}
