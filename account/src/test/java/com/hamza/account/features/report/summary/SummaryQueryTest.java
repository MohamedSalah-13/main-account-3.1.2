package com.hamza.account.features.report.summary;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SummaryQueryTest {

    @Test
    @DisplayName("the cash is the treasury statement's movements, less an opening and a transfer between treasuries")
    void theCashIsTheTreasuryStatements() {
        String sql = JdbcSummaryRepository.CASH_SQL;

        assertTrue(sql.contains("FROM treasury_balance"), sql);
        assertTrue(sql.endsWith("source_type NOT IN (0, 10, 11)"), sql);
        assertEquals(2, sql.chars().filter(character -> character == '?').count());
    }

    @Test
    @DisplayName("the low stock is the notification's own view, the lowest first, with how many in all")
    void theLowStockIsTheNotificationsView() {
        String sql = JdbcSummaryRepository.LOW_STOCK_SQL;

        assertTrue(sql.contains("FROM mini_quantity_view v"), sql);
        assertTrue(sql.contains("COUNT(*) OVER () AS total"), sql);
        assertTrue(sql.contains("ORDER BY v.balance, v.nameItem"), sql);
    }

    @Test
    @DisplayName("the old dashboard view is not defined again, and its DROP stays for the installs that have it")
    void theOldDashboardViewStaysGone() throws IOException {
        String views = Files.readString(Path.of("src", "main", "resources", "db", "migration", "R__views.sql"));

        assertFalse(views.contains("CREATE VIEW daily_dashboard_report"),
                "daily_dashboard_report is back: it counted a day's sales before their discounts with nothing "
                        + "returned taken off. The summary reads features/report/summary.");
        assertTrue(views.contains("DROP VIEW IF EXISTS daily_dashboard_report;"));
    }
}
