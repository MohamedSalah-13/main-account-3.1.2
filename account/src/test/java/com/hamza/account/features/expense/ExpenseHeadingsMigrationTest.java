package com.hamza.account.features.expense;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.treasury.WalletFee;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads V64 and {@code R__triggers.sql} and checks they do what the code assumes.
 * <p>
 * It says nothing about whether MySQL accepts them - V57 passed a test like this and failed on every
 * install with error 1833. Only migrating a schema from nothing says that, and it is recorded in
 * docs/expenses-plan.md with the day it was run.
 */
class ExpenseHeadingsMigrationTest {

    private static String read(String file) throws IOException {
        return Files.readString(Path.of("src/main/resources/db/migration", file));
    }

    private static String withoutComments(String sql) {
        return sql.replaceAll("(?m)--.*$", "");
    }

    @Test
    @DisplayName("the key onto the heading is found by its columns, not by the name V1 gave it")
    void foreignKeyIsFoundByColumns() throws IOException {
        String sql = withoutComments(read("V64__expense_headings.sql"));
        assertTrue(sql.contains("information_schema.KEY_COLUMN_USAGE"));
        assertTrue(sql.contains("COLUMN_NAME = 'type_code'"));
        assertTrue(sql.contains("REFERENCED_TABLE_NAME = 'expenses'"));
        int drop = sql.indexOf("CALL drop_expense_heading_foreign_key();");
        int modify = sql.indexOf("MODIFY COLUMN id INT AUTO_INCREMENT NOT NULL");
        int restore = sql.indexOf("'FOREIGN KEY (type_code) REFERENCES expenses (id)'");
        assertTrue(drop >= 0 && drop < modify && modify < restore,
                "MySQL refuses to change a column a key points at: drop, change, restore - in that order");
    }

    @Test
    @DisplayName("the wallet-fee key is the one the code looks for, written onto the name the code used to look for")
    void walletFeeKey() throws IOException {
        String sql = withoutComments(read("V64__expense_headings.sql"));
        assertTrue(sql.contains("SET system_key = '" + ExpenseHeading.WALLET_FEE + "'"));
        assertTrue(sql.contains("WHERE expenses_name = '" + WalletFee.EXPENSE_NAME + "'"));
    }

    @Test
    @DisplayName("no figure moves: nothing updates an amount, a date, a till or a heading of any expense")
    void noFigureMoves() throws IOException {
        String sql = withoutComments(read("V64__expense_headings.sql"));
        assertFalse(Pattern.compile("UPDATE\\s+expenses_details", Pattern.CASE_INSENSITIVE).matcher(sql).find());
        assertFalse(sql.contains("employee_cash_purpose"),
                "decision م-٣: old advances are not re-labelled by a migration");
    }

    @Test
    @DisplayName("each new key goes to whoever held the key it replaces, so nobody loses an ability on upgrade")
    void grants() throws IOException {
        String sql = withoutComments(read("V64__expense_headings.sql"));
        assertGrant(sql, "treasury.show", AppPermissions.EXPENSES_SHOW.value());
        assertGrant(sql, "expenses.update", AppPermissions.EXPENSES_HEADINGS_UPDATE.value());
        assertGrant(sql, "expenses.show", AppPermissions.EXPENSES_EXPORT.value());
        assertTrue(sql.indexOf("granted.permission_key = 'expenses.show'")
                        < sql.indexOf("granted.permission_key = 'expenses.export'"),
                "export is granted from expenses.show after expenses.show itself is granted");
    }

    private static void assertGrant(String sql, String held, String granted) {
        assertTrue(Pattern.compile("held\\.permission_key = '" + Pattern.quote(held) + "'\\s+JOIN auth_permission "
                        + "granted ON granted\\.permission_key = '" + Pattern.quote(granted) + "'")
                .matcher(sql).find(), granted + " is granted to holders of " + held);
    }

    @Test
    @DisplayName("an expense's insert and update are audited, and so are the headings")
    void auditTriggers() throws IOException {
        String triggers = read("R__triggers.sql");
        for (String trigger : new String[]{"audit_expenses_details_insert", "audit_expenses_details_update",
                "audit_expenses_details_delete", "audit_expenses_insert", "audit_expenses_update",
                "audit_expenses_delete"}) {
            assertTrue(triggers.contains("DROP TRIGGER IF EXISTS " + trigger + ";"), trigger + " is dropped first");
            assertTrue(triggers.contains("CREATE TRIGGER " + trigger), trigger + " is created");
        }
    }
}
