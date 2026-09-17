package com.hamza.account.controller.expense.report;

import com.hamza.account.features.expense.ExpenseFilter;
import javafx.scene.control.Tab;

/**
 * One report in the expense reports window. The window owns the scope - the period and the list's other
 * conditions - and each tab reads its report over it, draws it, and prints or exports what it drew.
 */
interface ExpenseReportTab {

    Tab tab();

    /** Reads the report over this scope, off the JavaFX thread. */
    void load(ExpenseFilter scope);

    /** A PDF of what is drawn. Asked only after the export permission was checked. */
    void print();

    /** A spreadsheet of what is drawn. Asked only after the export permission was checked. */
    void export();
}
