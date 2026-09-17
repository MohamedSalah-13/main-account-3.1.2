package com.hamza.account.features.expense;

import java.util.List;

/** Lets the report tests reach the list's package-private binder, so the two can be compared. */
public final class ExpenseQueryAccess {

    private ExpenseQueryAccess() {
    }

    public static void bindWhere(List<Object> values, ExpenseFilter filter) {
        JdbcExpenseRepository.bindWhere(values, filter);
    }
}
