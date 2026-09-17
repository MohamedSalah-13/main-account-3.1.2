package com.hamza.account.features.expense.report;

import com.hamza.account.features.expense.ExpenseFilter;
import com.hamza.account.features.expense.ExpenseQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The report statements, pinned - and held to the list's joins and conditions, and to the one definition of
 * net revenue the profit and loss screen reads.
 */
class ExpenseReportQueryTest {

    private static final ExpenseFilter EVERYTHING = new ExpenseFilter(LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 30), 3, 1, 9, BigDecimal.ONE, BigDecimal.TEN, "كهرباء", 0, 50);
    private static final ExpenseFilter NOTHING = ExpenseFilter.between(null, null);

    private static int placeholders(String sql) {
        return (int) sql.chars().filter(character -> character == '?').count();
    }

    @Test
    @DisplayName("the report by heading, character for character")
    void headingTotalsIsPinned() {
        assertEquals("SELECT d.type_code AS heading_id, COUNT(*) AS expense_count, SUM(d.amount) AS total\n"
                        + ExpenseQuery.fromSql() + ExpenseQuery.whereSql(EVERYTHING) + "GROUP BY d.type_code",
                ExpenseReportQuery.headingTotalsSql(EVERYTHING));
    }

    @Test
    @DisplayName("every report reads through the list's joins and binds exactly the list's values")
    void reportsShareTheListsWhere() {
        for (ExpenseFilter filter : List.of(EVERYTHING, NOTHING)) {
            int bound = ExpenseQuery.whereValues(filter).size();
            assertEquals(ExpenseQuery.whereParameterCount(filter), bound);
            List<String> statements = new java.util.ArrayList<>(List.of(ExpenseReportQuery.headingTotalsSql(filter),
                    ExpenseReportQuery.headingDaysSql(filter), ExpenseReportQuery.daysSql(filter)));
            for (ExpenseDimension dimension : ExpenseDimension.values()) {
                statements.add(ExpenseReportQuery.dimensionSql(filter, dimension));
            }
            for (String sql : statements) {
                assertTrue(sql.contains(ExpenseQuery.fromSql() + ExpenseQuery.whereSql(filter)), sql);
                assertEquals(bound, placeholders(sql), sql);
            }
        }
    }

    @Test
    @DisplayName("the list binder and the report binder are one binder")
    void listAndReportBindAlike() {
        List<Object> list = new java.util.ArrayList<>();
        com.hamza.account.features.expense.ExpenseQueryAccess.bindWhere(list, EVERYTHING);
        assertEquals(ExpenseQuery.whereValues(EVERYTHING), list);
    }

    @Test
    @DisplayName("a dimension groups by its key alone and reads its name as an aggregate")
    void dimensionIsGroupedByItsKey() {
        assertTrue(ExpenseReportQuery.dimensionSql(NOTHING, ExpenseDimension.TREASURY)
                .startsWith("SELECT d.treasury_id AS dimension_key, MAX(t.t_name) AS dimension_label,"));
        assertTrue(ExpenseReportQuery.dimensionSql(NOTHING, ExpenseDimension.MONTH)
                .endsWith("GROUP BY YEAR(d.date) * 100 + MONTH(d.date)"));
    }

    @Test
    @DisplayName("net sales is document_profit's net revenue, read off the headers with the bounds inside each branch")
    void netSalesIsDocumentProfitsRevenue() throws IOException {
        String views = Files.readString(Path.of("src/main/resources/db/migration/R__views.sql"), StandardCharsets.UTF_8);
        String view = views.substring(views.indexOf("CREATE VIEW document_profit AS"),
                views.indexOf("DROP VIEW IF EXISTS total_sales_names_table"));
        assertTrue(view.contains(ExpenseReportQuery.SALE_NET_REVENUE + " "), "the sale's revenue in the view");
        assertTrue(view.contains(ExpenseReportQuery.RETURN_NET_REVENUE + ","), "the return's revenue in the view");
        assertTrue(view.contains("FROM total_sales ts") && view.contains("FROM total_sales_re tsr"));

        assertEquals(4, placeholders(ExpenseReportQuery.NET_SALES_DAYS_SQL));
        assertTrue(ExpenseReportQuery.NET_SALES_DAYS_SQL.contains("WHERE ts.invoice_date BETWEEN ? AND ?"));
        assertTrue(ExpenseReportQuery.NET_SALES_DAYS_SQL.contains("WHERE tsr.invoice_date BETWEEN ? AND ?"));
        assertTrue(!ExpenseReportQuery.NET_SALES_DAYS_SQL.contains("document_profit"),
                "the view aggregates every sales line before it can filter");
    }
}
