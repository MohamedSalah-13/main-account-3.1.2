package com.hamza.account.features.profitloss.yearly;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The breakdown's statement against the view it reads, as text - there is no database here, and a column
 * renamed in {@code R__views.sql} would otherwise be found by the first person to open the report.
 */
class JdbcYearlyBreakdownRepositoryTest {

    private static final Path VIEWS = Path.of("src", "main", "resources", "db", "migration", "R__views.sql");
    private static final Pattern SELECTED = Pattern.compile("SELECT(.*?)FROM", Pattern.DOTALL);

    private static String viewBody() throws IOException {
        String sql = Files.readString(VIEWS).replaceAll("(?m)--.*$", "");
        int start = sql.indexOf("CREATE VIEW view_yearly_monthly_report AS");
        assertTrue(start >= 0, "view_yearly_monthly_report is gone from R__views.sql");
        int end = sql.indexOf("DROP VIEW IF EXISTS", start);
        return end < 0 ? sql.substring(start) : sql.substring(start, end);
    }

    private static List<String> selectedColumns() {
        Matcher matcher = SELECTED.matcher(JdbcYearlyBreakdownRepository.BREAKDOWN_SQL);
        assertTrue(matcher.find());
        return List.of(matcher.group(1).trim().split("\\s*,\\s*"));
    }

    @Test
    @DisplayName("every column the breakdown reads is one the view answers")
    void everyColumnIsTheViews() throws IOException {
        String view = viewBody();
        List<String> columns = selectedColumns();
        assertFalse(columns.isEmpty());
        for (String column : columns) {
            boolean named = column.equals("report_month")
                    ? view.contains("AS report_month")
                    : view.contains("AS " + column + ",") || view.contains("AS " + column + "\n");
            assertTrue(named, "view_yearly_monthly_report has no column " + column);
        }
    }

    @Test
    @DisplayName("the breakdown never reads the view's profit or expenses - those are the statement's")
    void theProfitIsNotReadHere() {
        String sql = JdbcYearlyBreakdownRepository.BREAKDOWN_SQL;
        assertFalse(sql.contains("estimated_net_profit"), "the profit comes from the statement, not the view");
        assertFalse(sql.contains("expenses"), "the expenses come from the statement, not the view");
    }
}
