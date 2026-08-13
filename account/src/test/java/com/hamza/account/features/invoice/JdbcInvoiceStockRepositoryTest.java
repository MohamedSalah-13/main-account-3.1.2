package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcInvoiceStockRepositoryTest {

    @Test
    void locksItemsInStableOrder() {
        assertEquals("SELECT id,nameItem FROM items WHERE id IN (?,?,?) ORDER BY id FOR UPDATE",
                JdbcInvoiceStockRepository.lockItemsSql(3));
    }

    @Test
    void locksOriginalLinesForEveryDocumentFamily() {
        assertOriginalSql(DocumentType.SALES, "sales", "num");
        assertOriginalSql(DocumentType.PURCHASE, "purchase", "num");
        assertOriginalSql(DocumentType.SALES_RETURN, "sales_re", "item_id");
        assertOriginalSql(DocumentType.PURCHASE_RETURN, "purchase_re", "item_id");
    }

    @Test
    void totalBalanceIncludesAllStockMovementKinds() {
        String sql = normalize(JdbcInvoiceStockRepository.baseBalancesSql(2));

        assertTrue(sql.contains("quantitypurchase"));
        assertTrue(sql.contains("quantitysalesre"));
        assertTrue(sql.contains("quantitysales"));
        assertTrue(sql.contains("quantitypurchasere"));
        assertTrue(sql.contains("tostock"));
        assertTrue(sql.contains("fromstock"));
        assertTrue(sql.contains("adjustment"));
        assertEquals(3, sql.chars().filter(value -> value == '?').count());
    }

    @Test
    void expiryBalanceIncludesFourInvoiceMovementTablesAndStockFilters() {
        String sql = normalize(JdbcInvoiceStockRepository.expiryBalancesSql(2));

        assertTrue(sql.contains("from purchase p"));
        assertTrue(sql.contains("from sales_re r"));
        assertTrue(sql.contains("from sales s"));
        assertTrue(sql.contains("from purchase_re r"));
        assertTrue(sql.contains("group by item_id, expiration_date"));
        assertEquals(12, sql.chars().filter(value -> value == '?').count());
    }

    private static void assertOriginalSql(
            DocumentType type, String table, String itemColumn) {
        String sql = JdbcInvoiceStockRepository.originalLinesForUpdateSql(type);
        assertTrue(sql.contains("SELECT " + itemColumn + " AS item_id"));
        assertTrue(sql.contains("FROM " + table + " "));
        assertTrue(sql.endsWith("ORDER BY id FOR UPDATE"));
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
