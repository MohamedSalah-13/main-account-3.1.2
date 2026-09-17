package com.hamza.account.features.expense.report;

/** Where a line sits in a report laid out by heading. */
public enum ExpenseHeadingLineKind {

    /** A main heading: its own expenses and every heading under it. */
    MAIN,

    /** A heading under a main one. */
    SUB,

    /**
     * What was filed on a main heading itself, written under its children. It has no filter of its own on
     * the list - the list's heading condition takes a main heading together with its children - so a
     * report offers no "open the list" on it rather than one that opens a larger set.
     */
    DIRECT;

    /** Whether the list can be opened on exactly this line. */
    public boolean opensList() {
        return this != DIRECT;
    }
}
