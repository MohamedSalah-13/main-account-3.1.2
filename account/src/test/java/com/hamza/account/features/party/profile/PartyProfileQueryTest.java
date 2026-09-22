package com.hamza.account.features.party.profile;

import com.hamza.account.document.ItemNetLines;
import com.hamza.account.features.delegate.DelegateActivityQuery;
import com.hamza.account.features.events.PartyKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins a profile's statements character for character, with their parameter counts. The items
 * and the days are two readings of one set of documents and the summary checks them against each
 * other, so a column moved in either one is a profile that no longer adds up.
 */
class PartyProfileQueryTest {

    @Test
    void aCustomersItemsAreTheSalesNetLinesForThatCustomer() {
        assertEquals("""
                SELECT m.item_id AS item_id,
                       i.nameItem AS item_name,
                       COALESCE(u.unit_name, '') AS unit_name,
                       COALESCE(i.sub_num, 0) AS group_id,
                       COALESCE(g.name, '') AS group_name,
                       SUM(m.quantity) AS quantity,
                       SUM(m.amount) AS amount,
                       SUM(m.returned_amount) AS returned_amount,
                       SUM(m.documents) AS documents
                FROM (%s) m
                         JOIN items i ON i.id = m.item_id
                         LEFT JOIN units u ON u.unit_id = i.unit_id
                         LEFT JOIN sub_group g ON g.id = i.sub_num
                GROUP BY m.item_id, i.nameItem, u.unit_name, i.sub_num, g.name
                ORDER BY SUM(m.amount) - SUM(m.returned_amount) DESC, item_name"""
                .formatted(ItemNetLines.SALES.perItemSql(true)), PartyProfileQuery.itemsSql(PartyKind.CUSTOMER));
    }

    @Test
    void aSuppliersItemsAreThePurchasesNetLines() {
        assertTrue(PartyProfileQuery.itemsSql(PartyKind.SUPPLIER).contains(ItemNetLines.PURCHASES.perItemSql(true)));
    }

    @Test
    void aCustomersDays() {
        assertEquals("""
                SELECT m.day AS day,
                       SUM(m.documents) AS documents,
                       SUM(m.net) AS net,
                       SUM(m.cash) AS cash,
                       SUM(m.header_discount) AS header_discount,
                       SUM(m.returns) AS returns,
                       SUM(m.returned) AS returned,
                       SUM(m.refunded) AS refunded,
                       SUM(m.returns_header_discount) AS returns_header_discount
                FROM (SELECT h.invoice_date AS day,
                             COUNT(*) AS documents,
                             SUM(h.total - h.discount) AS net,
                             SUM(h.paid_up) AS cash,
                             SUM(h.discount) AS header_discount,
                             0 AS returns,
                             0 AS returned,
                             0 AS refunded,
                             0 AS returns_header_discount
                      FROM total_sales h
                      WHERE h.sup_code = ? AND h.invoice_date BETWEEN ? AND ?
                      GROUP BY h.invoice_date
                      UNION ALL
                      SELECT r.invoice_date, 0, 0, 0, 0,
                             COUNT(*),
                             SUM(r.total - r.discount),
                             SUM(r.paid_from_treasury),
                             SUM(r.discount)
                      FROM total_sales_re r
                      WHERE r.sup_id = ? AND r.invoice_date BETWEEN ? AND ?
                      GROUP BY r.invoice_date) m
                GROUP BY m.day
                ORDER BY m.day""", PartyProfileQuery.daysSql(PartyKind.CUSTOMER));
    }

    @Test
    void aSuppliersDaysReadThePurchasesAndTheirCashColumns() {
        String sql = PartyProfileQuery.daysSql(PartyKind.SUPPLIER);
        assertTrue(sql.contains("FROM total_buy h"));
        assertTrue(sql.contains("SUM(h.paid_up) AS cash"));
        assertTrue(sql.contains("FROM total_buy_re r"));
        assertTrue(sql.contains("SUM(r.paid_to_treasury)"));
    }

    @Test
    void theLastDocumentIsAskedWithoutAPeriod() {
        assertEquals("SELECT MAX(h.invoice_date) AS last_day FROM total_sales h WHERE h.sup_code = ?",
                PartyProfileQuery.lastDocumentSql(PartyKind.CUSTOMER));
        assertEquals("SELECT MAX(h.invoice_date) AS last_day FROM total_buy h WHERE h.sup_code = ?",
                PartyProfileQuery.lastDocumentSql(PartyKind.SUPPLIER));
    }

    @Test
    void eachParameterCountIsItsStatementsOwn() {
        for (PartyKind kind : PartyKind.values()) {
            assertEquals(PartyProfileQuery.ITEMS_PARAMETERS, marks(PartyProfileQuery.itemsSql(kind)));
            assertEquals(PartyProfileQuery.DAYS_PARAMETERS, marks(PartyProfileQuery.daysSql(kind)));
            assertEquals(PartyProfileQuery.LAST_DOCUMENT_PARAMETERS, marks(PartyProfileQuery.lastDocumentSql(kind)));
        }
    }

    /**
     * A document's net is the delegate report's expression, so a customer's profile and his
     * delegate's month count one invoice the same way. And the ledger's {@code purchase} column,
     * where credit notes live, is never read: a note is not a sale.
     */
    @Test
    void aDocumentIsValuedAsTheDelegateActivityValuesIt() {
        String days = PartyProfileQuery.daysSql(PartyKind.CUSTOMER);
        assertTrue(days.contains("SUM(h.total - h.discount)"));
        assertTrue(DelegateActivityQuery.ACTIVITY_SQL.contains("total - discount"),
                "the delegate report changed its net - check that the two still agree");
        assertFalse(days.contains("purchase"));
        assertFalse(days.contains("customers_accounts"));
    }

    private static long marks(String sql) {
        return sql.chars().filter(c -> c == '?').count();
    }
}
