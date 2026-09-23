package com.hamza.account.features.report.monthly;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonthlyTotalsQueryTest {

    @Test
    @DisplayName("the sales read their invoices and returns, each grouped in the union, never joined")
    void theSalesStatement() {
        assertEquals("""
                SELECT day,
                       SUM(invoices) AS invoices, SUM(gross) AS gross, SUM(discount) AS discount,
                       SUM(return_documents) AS return_documents, SUM(returns_gross) AS returns_gross,
                       SUM(returns_discount) AS returns_discount
                FROM (SELECT d.invoice_date AS day, 1 AS invoices, d.total AS gross, d.discount AS discount,
                             0 AS return_documents, 0 AS returns_gross, 0 AS returns_discount
                      FROM total_sales d
                      UNION ALL
                      SELECT r.invoice_date, 0, 0, 0, 1, r.total, r.discount
                      FROM total_sales_re r) documents
                GROUP BY day
                ORDER BY day""", MonthlyTotalsQuery.daysSql(MonthlySide.SALES));
    }

    @Test
    @DisplayName("the purchases are the same statement over their own two tables")
    void thePurchasesStatement() {
        String sql = MonthlyTotalsQuery.daysSql(MonthlySide.PURCHASES);

        assertEquals(MonthlyTotalsQuery.daysSql(MonthlySide.SALES)
                .replace("total_sales_re", "total_buy_re").replace("total_sales", "total_buy"), sql);
        assertTrue(sql.contains("FROM total_buy d") && sql.contains("FROM total_buy_re r"));
        assertFalse(sql.contains("total_sales"));
    }

    @Test
    @DisplayName("it reads the headers alone - not document_profit, which groups every line first")
    void itReadsTheHeadersAlone() {
        for (MonthlySide side : MonthlySide.values()) {
            String sql = MonthlyTotalsQuery.daysSql(side);
            assertFalse(sql.contains("document_profit"));
            assertFalse(sql.contains("JOIN"), "a day's invoices joined to its returns would multiply them");
            assertFalse(sql.contains("?"), "no parameters: the folding into months is the report's");
        }
    }

    @Test
    @DisplayName("the two monthly views stay gone, and their DROPs stay for the installs that have them")
    void theOldViewsStayGone() throws IOException {
        String views = Files.readString(Path.of("src", "main", "resources", "db", "migration", "R__views.sql"))
                .replaceAll("(?m)--.*$", "");
        for (String view : new String[]{"view_monthly_sales", "view_monthly_purchase"}) {
            assertFalse(views.contains("CREATE VIEW " + view),
                    view + " is back: it summed the invoices before their discounts with nothing returned taken "
                            + "off - a second definition of a month's sales. The report reads MonthlyTotalsQuery.");
            assertTrue(views.contains("DROP VIEW IF EXISTS " + view + ";"),
                    "the DROP for " + view + " is gone, so an install that ran an older copy keeps it");
        }
    }

    @Test
    @DisplayName("the bounded days are the same statement with the period on each side, four parameters")
    void theBoundedDaysAreTheSameStatement() {
        for (MonthlySide side : MonthlySide.values()) {
            String bounded = MonthlyTotalsQuery.daysBetweenSql(side);
            assertEquals(MonthlyTotalsQuery.daysSql(side),
                    bounded.replace("\n                      WHERE d.invoice_date BETWEEN ? AND ?", "")
                            .replace("\n                      WHERE r.invoice_date BETWEEN ? AND ?", ""),
                    "one definition of a day's figures, with and without a period");
            assertEquals(4, bounded.chars().filter(character -> character == '?').count());
        }
    }
}
