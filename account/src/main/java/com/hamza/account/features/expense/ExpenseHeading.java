package com.hamza.account.features.expense;

import java.util.Objects;

/**
 * A heading an expense is filed under, as a row.
 * <p>
 * <b>What this replaces is the point.</b> {@code expenses} has been a real table with a unique name
 * since V1, and against it stood {@code ExpensesType} - six constants whose ids were matched to those
 * rows by hand. The entry screen offered those six and nothing else, so the "عمولات تحويل" heading V21
 * seeded for wallet fees was not on it, and no screen anywhere could add a seventh. A table nobody could
 * add a row to was not a table - the {@code UsersType} defect again, at a different table.
 *
 * @param parentId        the main heading this one sits under, or {@code null} for a main heading.
 *                        Two levels and no more - {@link ExpenseHeadingRules} holds that
 * @param parentName      that main heading's name, read with the row, or {@code null}
 * @param systemKey       what the system finds this heading by, for the few it depends on -
 *                        {@link #WALLET_FEE}. {@code null} for every heading a shop created
 * @param employeePayment a heading employees are paid under. The employee payment screen offers these
 *                        and the expenses screen does not (docs/expenses-plan.md ق-٥)
 */
public record ExpenseHeading(int id,
                             String name,
                             Integer parentId,
                             String parentName,
                             boolean active,
                             String systemKey,
                             boolean employeePayment) {

    /**
     * The heading a wallet fee is posted under. It used to be found by its name, which was right only
     * while nobody could rename a heading - and renaming one is the first thing the headings screen
     * allows. V64 wrote this key onto the row, reading the name once, at the one moment it was certain.
     */
    public static final String WALLET_FEE = "WALLET_FEE";

    /** What separates a main heading from its sub-heading wherever the two are written together. */
    public static final String PATH_SEPARATOR = " › ";

    public ExpenseHeading {
        Objects.requireNonNull(name, "name");
    }

    public boolean isMain() {
        return parentId == null;
    }

    /** A heading the system depends on: it may be renamed, never stopped, moved or deleted. */
    public boolean isSystem() {
        return systemKey != null && !systemKey.isBlank();
    }

    /** "الإدارية › الكهرباء" for a sub-heading, the name alone for a main one. */
    public String path() {
        return parentName == null || parentName.isBlank() ? name : parentName + PATH_SEPARATOR + name;
    }
}
