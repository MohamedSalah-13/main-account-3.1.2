package com.hamza.account.features.expense.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V65, read as text. The first draft failed on the first MySQL it met - its description was 62 characters
 * for a {@code VARCHAR(50)} column - and nothing in a green build could see it; this is what can.
 */
class ExpenseReportsMigrationTest {

    private static final Path V65 = Path.of("src/main/resources/db/migration/V65__expense_reports_permission.sql");

    @Test
    @DisplayName("the permission's description fits auth_permission.description, VARCHAR(50)")
    void descriptionFits() throws IOException {
        String sql = Files.readString(V65, StandardCharsets.UTF_8);
        Matcher row = Pattern.compile("VALUES \\('expenses\\.reports', '([^']*)'").matcher(sql);
        assertTrue(row.find(), "the row for expenses.reports");
        assertTrue(row.group(1).length() <= 50, row.group(1).length() + " characters: " + row.group(1));
    }

    @Test
    @DisplayName("it is granted to whoever may see the list, so nobody loses the reports on upgrade")
    void grantedToListViewers() throws IOException {
        String sql = Files.readString(V65, StandardCharsets.UTF_8);
        assertTrue(sql.contains("held.permission_key = 'expenses.show'"));
        assertTrue(sql.contains("granted.permission_key = 'expenses.reports'"));
    }
}
