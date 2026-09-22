package com.hamza.account.document;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the per-item lines character for character. Three reports read from this statement, and a
 * column swapped here would move every one of them at once without failing any of them.
 */
class ItemNetLinesTest {

    @Test
    void salesForOneCustomer() {
        assertEquals("""
                SELECT d.num AS item_id,
                       SUM(d.quantity * d.type_value) AS quantity,
                       SUM(d.total_sel_price - d.discount) AS amount,
                       0 AS returned_amount,
                       COUNT(DISTINCT d.invoice_number) AS documents
                FROM sales d JOIN total_sales h ON h.invoice_number = d.invoice_number
                WHERE h.invoice_date BETWEEN ? AND ? AND h.sup_code = ?
                GROUP BY d.num
                UNION ALL
                SELECT r.item_id AS item_id,
                       -SUM(r.quantity * r.type_value) AS quantity,
                       0 AS amount,
                       SUM(r.total_sel_price - r.discount) AS returned_amount,
                       0 AS documents
                FROM sales_re r JOIN total_sales_re rh ON rh.id = r.invoice_number
                WHERE rh.invoice_date BETWEEN ? AND ? AND rh.sup_id = ?
                GROUP BY r.item_id""", ItemNetLines.SALES.perItemSql(true));
        assertEquals(6, ItemNetLines.SALES.parameterCount(true));
    }

    @Test
    void purchasesForEverySupplier() {
        assertEquals("""
                SELECT d.num AS item_id,
                       SUM(d.quantity * d.type_value) AS quantity,
                       SUM(d.quantity * d.price - d.discount) AS amount,
                       0 AS returned_amount,
                       COUNT(DISTINCT d.invoice_number) AS documents
                FROM purchase d JOIN total_buy h ON h.invoice_number = d.invoice_number
                WHERE h.invoice_date BETWEEN ? AND ?
                GROUP BY d.num
                UNION ALL
                SELECT r.item_id AS item_id,
                       -SUM(r.quantity * r.type_value) AS quantity,
                       0 AS amount,
                       SUM(r.quantity * r.price - r.discount) AS returned_amount,
                       0 AS documents
                FROM purchase_re r JOIN total_buy_re rh ON rh.id = r.invoice_number
                WHERE rh.invoice_date BETWEEN ? AND ?
                GROUP BY r.item_id""", ItemNetLines.PURCHASES.perItemSql(false));
        assertEquals(4, ItemNetLines.PURCHASES.parameterCount(false));
    }

    @Test
    void theParameterCountIsTheStatementsOwn() {
        for (ItemNetLines lines : ItemNetLines.values()) {
            for (boolean forOneParty : new boolean[]{true, false}) {
                long marks = lines.perItemSql(forOneParty).chars().filter(c -> c == '?').count();
                assertEquals(lines.parameterCount(forOneParty), marks, lines + " " + forOneParty);
            }
        }
    }

    /** The delegate report's line amount, which this class generalises - the two must not drift. */
    @Test
    void aSalesLineIsValuedAsTheDelegateDetailValuesIt() {
        String sql = ItemNetLines.SALES.perItemSql(false);
        assertTrue(sql.contains("SUM(d.total_sel_price - d.discount)"));
        assertTrue(sql.contains("SUM(r.total_sel_price - r.discount)"));
        assertFalse(sql.contains("JOIN sales_re"), "each side is grouped before the union, never joined to the other");
    }
}
