package com.hamza.account.features.profitloss.statement;

import com.hamza.account.features.stockcount.StockCountHistoryQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfitLossStatementQueryTest {

    private static String views() throws IOException {
        return Files.readString(Path.of("src", "main", "resources", "db", "migration", "R__views.sql"),
                StandardCharsets.UTF_8);
    }

    /** The text of document_profit's definition, from its CREATE to the view after it. */
    private static String documentProfit() throws IOException {
        String views = views();
        int start = views.indexOf("CREATE VIEW document_profit");
        int end = views.indexOf("DROP VIEW", start);
        return views.substring(start, end);
    }

    private static long parameters(String sql) {
        return sql.chars().filter(character -> character == '?').count();
    }

    @Test
    @DisplayName("a document's revenue and cost are document_profit's own arithmetic")
    void theViewsArithmetic() throws IOException {
        String view = documentProfit();
        assertTrue(view.contains("ts.total - ts.discount"));
        assertTrue(view.contains("tsr.total - tsr.discount"));
        assertTrue(view.contains("SUM(total_buy_price)"));
        assertTrue(view.contains("c.invoice_number = tsr.id"), "a return's lines point at it by its id");

        assertTrue(ProfitLossStatementQuery.SALE_COST.contains("SUM(s.total_buy_price)"));
        assertTrue(ProfitLossStatementQuery.SALE_COST.contains("s.invoice_number = ts.invoice_number"));
        assertTrue(ProfitLossStatementQuery.RETURN_COST.contains("SUM(r.total_buy_price)"));
        assertTrue(ProfitLossStatementQuery.RETURN_COST.contains("r.invoice_number = tsr.id"));
        assertTrue(ProfitLossStatementQuery.BREAKDOWN_SQL.contains("tsr.total - tsr.discount"));
        assertTrue(ProfitLossStatementQuery.MOVEMENTS_SQL.contains("ts.total - ts.discount AS net_sales"));
        assertTrue(ProfitLossStatementQuery.MOVEMENTS_SQL.contains("-(tsr.total - tsr.discount)"));
    }

    @Test
    @DisplayName("the expenses are the rows and the date the statement's expense column sums")
    void theStatementsExpenses() throws IOException {
        String dao = Files.readString(Path.of("src", "main", "java", "com", "hamza", "account", "features",
                "profitloss", "ProfitLossDao.java"), StandardCharsets.UTF_8);
        assertTrue(dao.contains("SELECT date,0,0,amount FROM expenses_details"));
        assertTrue(ProfitLossStatementQuery.EXPENSES_BY_HEADING_SQL.contains("SUM(d.amount)"));
        assertTrue(ProfitLossStatementQuery.EXPENSES_BY_HEADING_SQL.contains("WHERE d.date BETWEEN ? AND ?"));
        assertTrue(ProfitLossStatementQuery.EXPENSES_BY_HEADING_SQL.contains("LEFT JOIN expenses e"),
                "an expense whose heading is gone still counts");
    }

    @Test
    @DisplayName("a count's difference is the balance's own expression, over posted counts only")
    void theCountsDifference() throws IOException {
        String views = views();
        assertTrue(views.contains("SUM(" + StockCountHistoryQuery.DIFFERENCE + ")"));
        assertTrue(views.contains("WHERE sc.status = 'POSTED'"));
        assertTrue(ProfitLossStatementQuery.STOCK_COUNT_SQL.contains(StockCountHistoryQuery.DIFFERENCE));
        assertTrue(ProfitLossStatementQuery.STOCK_COUNT_SQL.contains("WHERE sc.status = 'POSTED'"));
    }

    @Test
    @DisplayName("each bound sits inside its branch, and each statement takes the parameters it is bound with")
    void boundsAndParameters() {
        assertEquals(4, parameters(ProfitLossStatementQuery.BREAKDOWN_SQL));
        assertTrue(ProfitLossStatementQuery.BREAKDOWN_SQL.contains("WHERE ts.invoice_date BETWEEN ? AND ?"));
        assertTrue(ProfitLossStatementQuery.BREAKDOWN_SQL.contains("WHERE tsr.invoice_date BETWEEN ? AND ?"));
        assertEquals(2, parameters(ProfitLossStatementQuery.EXPENSES_BY_HEADING_SQL));
        assertEquals(2, parameters(ProfitLossStatementQuery.STOCK_COUNT_SQL));
        assertEquals(2, parameters(ProfitLossStatementQuery.TILL_SQL));
        assertTrue(ProfitLossStatementQuery.TILL_SQL.contains("close_time >= ? AND close_time < ?"));
        assertEquals(6, parameters(ProfitLossStatementQuery.MOVEMENTS_SQL));
    }
}
