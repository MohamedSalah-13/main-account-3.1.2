package com.hamza.account.features.expense.budget;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V66, read as text.
 * <p>
 * A migration is only wrong when MySQL reads it, and this cannot stand in for running it against a
 * schema built from nothing. What it can do is hold the four decisions that a green build would
 * otherwise never see again - and the first of them is the one {@code V65} shipped wrong.
 */
class ExpenseBudgetMigrationTest {

    private static final Path V66 =
            Path.of("src/main/resources/db/migration/V66__expense_budget_and_recurring.sql");

    private static String sql() throws IOException {
        return Files.readString(V66, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("every permission description fits auth_permission.description, VARCHAR(50)")
    void descriptionsFit() throws IOException {
        Matcher rows = Pattern.compile("'(expenses\\.[a-z.]+)', '([^']*)'").matcher(sql());
        int found = 0;
        while (rows.find()) {
            found++;
            assertTrue(rows.group(2).length() <= 50,
                    rows.group(1) + " has a " + rows.group(2).length() + "-character description");
        }
        assertTrue(found >= 2, "the two new keys were not found at all; this test would pass vacuously");
    }

    @Test
    @DisplayName("both keys are granted to whoever manages the headings, so nobody loses one on upgrade")
    void grantedFromTheHeadingsPermission() throws IOException {
        String sql = sql();
        for (String key : List.of("expenses.budget.manage", "expenses.recurring.manage")) {
            assertTrue(sql.contains("granted.permission_key = '" + key + "'"), key + " is never granted");
        }
        assertTrue(sql.contains("held.permission_key = 'expenses.headings.update'"));
    }

    @Test
    @DisplayName("the unique index is on the generated month_key, not on a nullable month")
    void oneYearlyBudgetPerHeading() throws IOException {
        String sql = sql();
        // MySQL treats NULL in a unique index as distinct, so a unique over (heading, year, month) would
        // allow two yearly budgets for one heading - two ceilings, and no rule that says which wins.
        assertTrue(sql.contains("month_key  TINYINT AS (COALESCE(month, 0)) STORED"));
        assertTrue(sql.contains("UNIQUE (heading_id, year, month_key)"));
    }

    @Test
    @DisplayName("deleting a template does not touch the expenses recorded from it")
    void theLinkIsSetNullNotCascade() throws IOException {
        String sql = sql();
        assertTrue(sql.contains("FOREIGN KEY (recurring_id) REFERENCES expense_recurring (id) ON DELETE SET NULL"),
                "that cash really left the drawer; its history does not depend on the template surviving");
        assertTrue(sql.contains("add_budget_column_if_missing('expenses_details', 'recurring_id'"),
                "the column is added to a table that already exists on every install");
    }

    @Test
    @DisplayName("every helper it defines it also drops - a stray one is what the next migration calls")
    void helpersAreCleanedUp() throws IOException {
        String sql = sql();
        Matcher defined = Pattern.compile("CREATE PROCEDURE (\\w+)").matcher(sql);
        int found = 0;
        while (defined.find()) {
            found++;
            assertTrue(sql.contains("DROP PROCEDURE IF EXISTS " + defined.group(1) + ";"),
                    defined.group(1) + " is defined and left behind");
        }
        assertTrue(found >= 3, "the helpers were not found; this test would pass vacuously");
    }
}
