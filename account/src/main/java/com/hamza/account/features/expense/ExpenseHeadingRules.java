package com.hamza.account.features.expense;

import com.hamza.controlsfx.error.UserValidationException;

import java.util.List;

/**
 * What a heading may and may not become, decided over the headings as they stand.
 * <p>
 * No database here, and no JavaFX: every refusal is a message key and every rule is a question about
 * a plain list, so {@code ExpenseHeadingRulesTest} can ask each one. The uniqueness of a name is not
 * among them - that is the index's decision, and {@link ExpenseHeadingService} only asks first as a
 * courtesy.
 *
 * <h2>Two levels, and why the check is on both ends</h2>
 * A heading may sit under a main heading and no deeper. That is two separate refusals, because the
 * depth can be broken from either side: by placing a heading under one that is itself a sub-heading,
 * and by placing a heading that <i>already has</i> sub-headings under anything at all. Checking only
 * the first lets the second through, and the result is a third level no report was written to add up.
 *
 * <h2>A system heading</h2>
 * One the code finds by key ({@link ExpenseHeading#WALLET_FEE}) may be renamed - the name is the
 * shop's - but never stopped, moved or made a heading employees are paid under. Stopped, it would
 * leave every wallet collection with a fee nothing can post; moved, it changes what the fee reports
 * under without anybody choosing that.
 */
public final class ExpenseHeadingRules {

    /** {@code expenses.expenses_name} is {@code VARCHAR(50)} since V1. */
    public static final int NAME_MAX = 50;

    private ExpenseHeadingRules() {
    }

    /**
     * Refuses a draft the headings as they stand cannot take.
     *
     * @param draft    what the screen wants saved
     * @param existing every heading, active or not, as read from the database
     */
    public static void requireValid(ExpenseHeadingDraft draft, List<ExpenseHeading> existing)
            throws UserValidationException {
        if (draft.name().isEmpty()) {
            throw new UserValidationException("expense.heading.error.name");
        }
        if (draft.name().codePointCount(0, draft.name().length()) > NAME_MAX) {
            throw new UserValidationException("expense.heading.error.name.length");
        }

        ExpenseHeading stored = draft.isNew() ? null : find(existing, draft.id());
        if (!draft.isNew() && stored == null) {
            throw new UserValidationException("expense.heading.error.not.found");
        }

        if (draft.parentId() != null) {
            if (!draft.isNew() && draft.parentId() == draft.id()) {
                throw new UserValidationException("expense.heading.error.parent.self");
            }
            ExpenseHeading parent = find(existing, draft.parentId());
            if (parent == null) {
                throw new UserValidationException("expense.heading.error.parent.missing");
            }
            if (!parent.isMain()) {
                throw new UserValidationException("expense.heading.error.parent.depth");
            }
            if (!draft.isNew() && hasChildren(existing, draft.id())) {
                throw new UserValidationException("expense.heading.error.has.children");
            }
        }

        if (stored != null && stored.isSystem()) {
            if (!draft.active()) {
                throw new UserValidationException("expense.heading.error.system.stop");
            }
            if (!sameParent(stored.parentId(), draft.parentId())) {
                throw new UserValidationException("expense.heading.error.system.move");
            }
            if (draft.employeePayment()) {
                throw new UserValidationException("expense.heading.error.system.employee");
            }
        }

        // A main heading stopped while one under it is still offered would leave a sub-heading on the
        // entry screen whose parent is nowhere - and a report grouping it under a heading marked stopped.
        if (stored != null && stored.active() && !draft.active() && hasActiveChildren(existing, draft.id())) {
            throw new UserValidationException("expense.heading.error.stop.children");
        }
    }

    /**
     * Refuses to delete a heading the system depends on. What else holds a heading - its expenses, its
     * sub-headings - is {@code DeleteRegistry.EXPENSE_HEADINGS}'s to say, with the count in the message.
     */
    public static void requireDeletable(ExpenseHeading heading) throws UserValidationException {
        if (heading == null) {
            throw new UserValidationException("expense.heading.error.not.found");
        }
        if (heading.isSystem()) {
            throw new UserValidationException("expense.heading.error.system.delete");
        }
    }

    /**
     * Whether an expense may be filed under this heading from the expenses screen.
     * <p>
     * Not a stopped heading, and not one employees are paid under: paying an employee has one road,
     * the employee payment screen, which records what the payment was for. The expenses screen used to
     * accept a salary or an advance without that, so an advance showed on the employee's account as a
     * salary (docs/expenses-plan.md ع-٣).
     */
    public static void requireUsableForExpense(ExpenseHeading heading) throws UserValidationException {
        if (heading == null) {
            throw new UserValidationException("expense.error.heading");
        }
        if (heading.employeePayment()) {
            throw new UserValidationException("expense.error.heading.employee");
        }
        if (!heading.active()) {
            throw new UserValidationException("expense.error.heading.stopped");
        }
    }

    /** The other half: an employee is paid only under a heading marked for it. */
    public static void requireUsableForEmployee(ExpenseHeading heading) throws UserValidationException {
        if (heading == null) {
            throw new UserValidationException("employee.error.pay.heading");
        }
        if (!heading.employeePayment()) {
            throw new UserValidationException("expense.error.heading.not.employee");
        }
        if (!heading.active()) {
            throw new UserValidationException("expense.error.heading.stopped");
        }
    }

    static ExpenseHeading find(List<ExpenseHeading> headings, int id) {
        for (ExpenseHeading heading : headings) {
            if (heading.id() == id) {
                return heading;
            }
        }
        return null;
    }

    private static boolean hasChildren(List<ExpenseHeading> headings, int id) {
        return headings.stream().anyMatch(heading -> heading.parentId() != null && heading.parentId() == id);
    }

    private static boolean hasActiveChildren(List<ExpenseHeading> headings, int id) {
        return headings.stream().anyMatch(heading -> heading.active()
                && heading.parentId() != null && heading.parentId() == id);
    }

    private static boolean sameParent(Integer left, Integer right) {
        return left == null ? right == null : left.equals(right);
    }
}
