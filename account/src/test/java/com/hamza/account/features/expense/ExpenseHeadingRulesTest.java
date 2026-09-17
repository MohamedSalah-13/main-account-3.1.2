package com.hamza.account.features.expense;

import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExpenseHeadingRulesTest {

    private static final ExpenseHeading ADMIN = new ExpenseHeading(10, "إدارية", null, null, true, null, false);
    private static final ExpenseHeading POWER = new ExpenseHeading(11, "كهرباء", 10, "إدارية", true, null, false);
    private static final ExpenseHeading WATER = new ExpenseHeading(12, "مياه", null, null, true, null, false);
    private static final ExpenseHeading FEE = new ExpenseHeading(13, "عمولات تحويل", null, null, true,
            ExpenseHeading.WALLET_FEE, false);
    private static final List<ExpenseHeading> ALL = List.of(ADMIN, POWER, WATER, FEE);

    private static void refused(String key, Executable action) {
        UserValidationException refusal = assertThrows(UserValidationException.class, action);
        assertEquals(key, refusal.getMessage());
    }

    @Test
    @DisplayName("a name is required and fits the column")
    void name() {
        refused("expense.heading.error.name",
                () -> ExpenseHeadingRules.requireValid(new ExpenseHeadingDraft(0, "  ", null, true, false), ALL));
        refused("expense.heading.error.name.length", () -> ExpenseHeadingRules.requireValid(
                new ExpenseHeadingDraft(0, "ب".repeat(51), null, true, false), ALL));
        assertDoesNotThrow(() -> ExpenseHeadingRules.requireValid(
                new ExpenseHeadingDraft(0, "ب".repeat(50), null, true, false), ALL));
    }

    @Test
    @DisplayName("two levels: not under a sub-heading, and a heading with sub-headings goes under nothing")
    void depthIsCheckedFromBothEnds() {
        refused("expense.heading.error.parent.depth", () -> ExpenseHeadingRules.requireValid(
                new ExpenseHeadingDraft(0, "جديد", POWER.id(), true, false), ALL));
        refused("expense.heading.error.has.children", () -> ExpenseHeadingRules.requireValid(
                new ExpenseHeadingDraft(ADMIN.id(), ADMIN.name(), WATER.id(), true, false), ALL));
        assertDoesNotThrow(() -> ExpenseHeadingRules.requireValid(
                new ExpenseHeadingDraft(WATER.id(), WATER.name(), ADMIN.id(), true, false), ALL));
    }

    @Test
    @DisplayName("not under itself, and not under a heading that is gone")
    void parentMustExistAndNotBeSelf() {
        refused("expense.heading.error.parent.self", () -> ExpenseHeadingRules.requireValid(
                new ExpenseHeadingDraft(WATER.id(), WATER.name(), WATER.id(), true, false), ALL));
        refused("expense.heading.error.parent.missing", () -> ExpenseHeadingRules.requireValid(
                new ExpenseHeadingDraft(0, "جديد", 999, true, false), ALL));
    }

    @Test
    @DisplayName("a main heading is not stopped while a heading under it is still offered")
    void stoppingAParentWithActiveChildren() {
        refused("expense.heading.error.stop.children", () -> ExpenseHeadingRules.requireValid(
                new ExpenseHeadingDraft(ADMIN.id(), ADMIN.name(), null, false, false), ALL));
    }

    @Test
    @DisplayName("the heading the system depends on may be renamed, never stopped, moved or given to employees")
    void systemHeading() {
        assertDoesNotThrow(() -> ExpenseHeadingRules.requireValid(
                new ExpenseHeadingDraft(FEE.id(), "عمولة المحافظ", null, true, false), ALL));
        refused("expense.heading.error.system.stop", () -> ExpenseHeadingRules.requireValid(
                new ExpenseHeadingDraft(FEE.id(), FEE.name(), null, false, false), ALL));
        refused("expense.heading.error.system.move", () -> ExpenseHeadingRules.requireValid(
                new ExpenseHeadingDraft(FEE.id(), FEE.name(), ADMIN.id(), true, false), ALL));
        refused("expense.heading.error.system.employee", () -> ExpenseHeadingRules.requireValid(
                new ExpenseHeadingDraft(FEE.id(), FEE.name(), null, true, true), ALL));
        refused("expense.heading.error.system.delete", () -> ExpenseHeadingRules.requireDeletable(FEE));
        assertDoesNotThrow(() -> ExpenseHeadingRules.requireDeletable(WATER));
    }

    @Test
    @DisplayName("an edit of a heading that is gone is refused rather than inserted")
    void editOfAMissingHeading() {
        refused("expense.heading.error.not.found", () -> ExpenseHeadingRules.requireValid(
                new ExpenseHeadingDraft(404, "x", null, true, false), ALL));
    }

    @Test
    @DisplayName("the path writes a sub-heading under its main one")
    void path() {
        assertEquals("إدارية" + ExpenseHeading.PATH_SEPARATOR + "كهرباء", POWER.path());
        assertEquals("مياه", WATER.path());
    }
}
