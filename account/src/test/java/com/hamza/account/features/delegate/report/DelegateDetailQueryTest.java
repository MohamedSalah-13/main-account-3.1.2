package com.hamza.account.features.delegate.report;

import com.hamza.account.features.delegate.DelegateActivityQuery;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The detail statements, character for character, and the count each one binds. A statement
 * built from a template is pinned as the text it becomes: a swapped argument in the template
 * still produces valid SQL - it just groups sales by one key and returns by another.
 */
class DelegateDetailQueryTest {

    @Test
    void byCustomerIsReadOffTheDocuments() {
        assertEquals("""
                SELECT m.key_id,
                       n.name AS key_name,
                       SUM(m.measure) AS measure,
                       SUM(m.sales) AS sales,
                       SUM(m.returns) AS sales_returns
                FROM (SELECT ts.sup_code AS key_id, COUNT(*) AS measure, SUM(ts.total - ts.discount) AS sales, 0 AS returns
                      FROM total_sales ts
                      WHERE ts.delegate_id = ? AND ts.invoice_date BETWEEN ? AND ?
                      GROUP BY ts.sup_code
                      UNION ALL
                      SELECT tr.sup_id, 0, 0, SUM(tr.total - tr.discount)
                      FROM total_sales_re tr
                      WHERE tr.delegate_id = ? AND tr.invoice_date BETWEEN ? AND ?
                      GROUP BY tr.sup_id) m
                         JOIN custom n ON n.id = m.key_id
                GROUP BY m.key_id, key_name
                ORDER BY SUM(m.sales) - SUM(m.returns) DESC, key_name""", DelegateDetailQuery.BY_CUSTOMER_SQL);
    }

    /** A LEFT join: a customer whose area row was deleted still sold what he sold. */
    @Test
    void byAreaIsTheCustomersAreaAndKeepsACustomerWhoseAreaIsGone() {
        assertEquals("""
                SELECT m.key_id,
                       COALESCE(n.area_name, '') AS key_name,
                       SUM(m.measure) AS measure,
                       SUM(m.sales) AS sales,
                       SUM(m.returns) AS sales_returns
                FROM (SELECT cu.area_id AS key_id, COUNT(*) AS measure, SUM(ts.total - ts.discount) AS sales, 0 AS returns
                      FROM total_sales ts JOIN custom cu ON cu.id = ts.sup_code
                      WHERE ts.delegate_id = ? AND ts.invoice_date BETWEEN ? AND ?
                      GROUP BY cu.area_id
                      UNION ALL
                      SELECT cu.area_id, 0, 0, SUM(tr.total - tr.discount)
                      FROM total_sales_re tr JOIN custom cu ON cu.id = tr.sup_id
                      WHERE tr.delegate_id = ? AND tr.invoice_date BETWEEN ? AND ?
                      GROUP BY cu.area_id) m
                         LEFT JOIN table_area n ON n.id = m.key_id
                GROUP BY m.key_id, key_name
                ORDER BY SUM(m.sales) - SUM(m.returns) DESC, key_name""", DelegateDetailQuery.BY_AREA_SQL);
    }

    @Test
    void byItemIsReadOffTheLines() {
        assertEquals("""
                SELECT m.key_id,
                       n.nameItem AS key_name,
                       SUM(m.measure) AS measure,
                       SUM(m.sales) AS sales,
                       SUM(m.returns) AS sales_returns
                FROM (SELECT s.num AS key_id, SUM(s.quantity * s.type_value) AS measure,
                             SUM(s.total_sel_price - s.discount) AS sales, 0 AS returns
                      FROM sales s JOIN total_sales ts ON ts.invoice_number = s.invoice_number
                      WHERE ts.delegate_id = ? AND ts.invoice_date BETWEEN ? AND ?
                      GROUP BY s.num
                      UNION ALL
                      SELECT sr.item_id, -SUM(sr.quantity * sr.type_value), 0, SUM(sr.total_sel_price - sr.discount)
                      FROM sales_re sr JOIN total_sales_re tr ON tr.id = sr.invoice_number
                      WHERE tr.delegate_id = ? AND tr.invoice_date BETWEEN ? AND ?
                      GROUP BY sr.item_id) m
                         JOIN items n ON n.id = m.key_id
                GROUP BY m.key_id, key_name
                ORDER BY SUM(m.sales) - SUM(m.returns) DESC, key_name""", DelegateDetailQuery.BY_ITEM_SQL);
    }

    @Test
    void byGroupIsTheItemsSubGroup() {
        assertEquals("""
                SELECT m.key_id,
                       n.name AS key_name,
                       SUM(m.measure) AS measure,
                       SUM(m.sales) AS sales,
                       SUM(m.returns) AS sales_returns
                FROM (SELECT i.sub_num AS key_id, SUM(s.quantity * s.type_value) AS measure,
                             SUM(s.total_sel_price - s.discount) AS sales, 0 AS returns
                      FROM sales s JOIN total_sales ts ON ts.invoice_number = s.invoice_number JOIN items i ON i.id = s.num
                      WHERE ts.delegate_id = ? AND ts.invoice_date BETWEEN ? AND ?
                      GROUP BY i.sub_num
                      UNION ALL
                      SELECT i.sub_num, -SUM(sr.quantity * sr.type_value), 0, SUM(sr.total_sel_price - sr.discount)
                      FROM sales_re sr JOIN total_sales_re tr ON tr.id = sr.invoice_number JOIN items i ON i.id = sr.item_id
                      WHERE tr.delegate_id = ? AND tr.invoice_date BETWEEN ? AND ?
                      GROUP BY i.sub_num) m
                         JOIN sub_group n ON n.id = m.key_id
                GROUP BY m.key_id, key_name
                ORDER BY SUM(m.sales) - SUM(m.returns) DESC, key_name""", DelegateDetailQuery.BY_GROUP_SQL);
    }

    @Test
    void theHeaderDiscountsAreTwoFiguresSalesFirst() {
        assertEquals("""
                SELECT (SELECT COALESCE(SUM(discount), 0)
                        FROM total_sales
                        WHERE delegate_id = ? AND invoice_date BETWEEN ? AND ?),
                       (SELECT COALESCE(SUM(discount), 0)
                        FROM total_sales_re
                        WHERE delegate_id = ? AND invoice_date BETWEEN ? AND ?)""",
                DelegateDetailQuery.HEADER_DISCOUNT_SQL);
    }

    @Test
    void theCollectionsAreTheAttributedRowsThatMovedCash() {
        assertEquals("""
                SELECT ca.account_num,
                       ca.account_date,
                       cu.name,
                       ca.numberInv,
                       ca.paid,
                       COALESCE(t.t_name, '') AS treasury_name
                FROM customers_accounts ca
                         JOIN custom cu ON cu.id = ca.account_code
                         LEFT JOIN treasury t ON t.id = ca.treasury_id
                WHERE ca.delegate_id = ?
                  AND ca.account_date BETWEEN ? AND ?
                  AND ca.paid <> 0
                ORDER BY ca.account_date, ca.account_num""", DelegateDetailQuery.COLLECTIONS_SQL);
    }

    @Test
    void everyStatementBindsWhatItsFilterHandsIt() {
        DelegateDetailFilter filter = new DelegateDetailFilter(4, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        assertEquals(DelegateDetailQuery.BREAKDOWN_PARAMETERS, filter.breakdownParameters().length);
        assertEquals(DelegateDetailQuery.COLLECTIONS_PARAMETERS, filter.collectionParameters().length);
        for (DelegateBreakdown breakdown : DelegateBreakdown.values()) {
            assertEquals(DelegateDetailQuery.BREAKDOWN_PARAMETERS, placeholders(breakdown.sql()), breakdown.name());
        }
        assertEquals(DelegateDetailQuery.BREAKDOWN_PARAMETERS, placeholders(DelegateDetailQuery.HEADER_DISCOUNT_SQL));
        assertEquals(DelegateDetailQuery.COLLECTIONS_PARAMETERS, placeholders(DelegateDetailQuery.COLLECTIONS_SQL));
    }

    /**
     * The whole point of the package: a document's net here is the expression the activity query
     * sums, so a breakdown explains the performance report's figure rather than offering another.
     */
    @Test
    void aDocumentsNetIsTheActivityQuerysExpression() {
        assertTrue(DelegateActivityQuery.ACTIVITY_SQL.contains("SUM(total - discount) AS net"));
        for (DelegateBreakdown breakdown : DelegateBreakdown.values()) {
            if (!breakdown.readOffLines()) {
                assertTrue(breakdown.sql().contains("SUM(ts.total - ts.discount)"), breakdown.name());
                assertTrue(breakdown.sql().contains("SUM(tr.total - tr.discount)"), breakdown.name());
            }
        }
    }

    /** {@code purchase} is a debit or credit note: it is not cash and is nobody's collection. */
    @Test
    void noStatementReadsTheNoteColumn() {
        assertFalse(DelegateDetailQuery.COLLECTIONS_SQL.contains("purchase"));
    }

    private static int placeholders(String sql) {
        return (int) sql.chars().filter(character -> character == '?').count();
    }
}
