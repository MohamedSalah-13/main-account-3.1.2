package com.hamza.account.features.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The reports' statements, pinned - and each page and its count or totals built from one {@code WHERE}. */
class TierReportQueryTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);

    @Test
    @DisplayName("an item missing a price on any tier in use - only those tiers are asked about")
    void missing() {
        TierReportQuery.Statement page = TierReportQuery.missingPage(List.of(1, 3), "عصير", 100, 200);
        assertEquals("SELECT i.id, i.barcode, i.nameItem, i.sel_price1, i.sel_price2, i.sel_price3"
                + " FROM items i WHERE (i.sel_price1 <= 0 OR i.sel_price3 <= 0)"
                + " AND (i.nameItem LIKE ? OR i.barcode = ?) ORDER BY i.nameItem, i.id LIMIT ? OFFSET ?",
                page.sql());
        assertEquals(List.of("%عصير%", "عصير", 100, 200), page.parameters());

        TierReportQuery.Statement count = TierReportQuery.missingCount(List.of(1, 3), "عصير");
        assertEquals("SELECT COUNT(*) FROM items i WHERE (i.sel_price1 <= 0 OR i.sel_price3 <= 0)"
                + " AND (i.nameItem LIKE ? OR i.barcode = ?)", count.sql());
        assertEquals(List.of("%عصير%", "عصير"), count.parameters());
    }

    /**
     * Whole statements, character for character. The first draft asserted that the page contained its
     * {@code WHERE}, built the same way - and both lost the space a text block's indent strip takes, so the
     * test passed a statement MySQL refused ({@code ts.price_tier_idFROM}).
     */
    @Test
    @DisplayName("the page and its totals, whole, sharing one WHERE and one binder")
    void belowListSharesItsWhere() {
        TierReportQuery.Statement page = TierReportQuery.belowListPage(FROM, TO, "علي", 50, 0);
        TierReportQuery.Statement summary = TierReportQuery.belowListSummary(FROM, TO, "علي");
        String from = " FROM sales s JOIN total_sales ts ON ts.invoice_number = s.invoice_number"
                + " JOIN items i ON i.id = s.num JOIN units un ON un.unit_id = s.type"
                + " LEFT JOIN custom c ON c.id = ts.sup_code LEFT JOIN users u ON u.id = ts.user_id";
        String where = " WHERE s.list_price IS NOT NULL AND s.price < s.list_price - 0.005"
                + " AND ts.invoice_date BETWEEN ? AND ? AND (c.name LIKE ? OR i.nameItem LIKE ? OR u.user_name LIKE ?)";
        assertEquals("SELECT ts.invoice_number, ts.invoice_date, c.name AS customer, u.user_name, i.nameItem,"
                + " un.unit_name, s.quantity, s.list_price, s.price,"
                + " ROUND((s.list_price - s.price) * s.quantity, 2) AS given, ts.price_tier_id"
                + from + where + " ORDER BY ts.invoice_date DESC, ts.invoice_number DESC, s.id LIMIT ? OFFSET ?",
                page.sql());
        assertEquals("SELECT COUNT(*), COUNT(DISTINCT s.invoice_number),"
                + " COALESCE(SUM(ROUND((s.list_price - s.price) * s.quantity, 2)), 0)" + from + where, summary.sql());
        assertEquals(List.of(FROM, TO, "%علي%", "%علي%", "%علي%", 50, 0), page.parameters());
        assertEquals(List.of(FROM, TO, "%علي%", "%علي%", "%علي%"), summary.parameters());
    }

    @Test
    @DisplayName("no search, no search condition")
    void noSearch() {
        TierReportQuery.Statement summary = TierReportQuery.belowListSummary(FROM, TO, " ");
        assertEquals(List.of(FROM, TO), summary.parameters());
        assertEquals(List.of(), TierReportQuery.missingCount(List.of(1), null).parameters());
    }

    @Test
    @DisplayName("the fill's statements name a tier's column only through PriceTiers")
    void fillStatements() {
        assertEquals("UPDATE items SET sel_price2 = ? WHERE id = ? AND sel_price2 = ?", TierFillQuery.writeItemSql(2));
        assertEquals("UPDATE items_units SET sel_price = ? WHERE items_id = ? AND unit = ? AND sel_price = ?",
                TierFillQuery.writeUnitSql(1));
        assertEquals("SELECT id FROM items ORDER BY id FOR UPDATE", TierFillQuery.LOCK_ITEMS_SQL);
        assertEquals("SELECT items_id FROM items_units ORDER BY items_id, unit FOR UPDATE", TierFillQuery.LOCK_UNITS_SQL);
        assertTrue(TierFillQuery.UNITS_SQL.contains("WHERE iu.unit <> i.unit_id"),
                "the base unit is items.unit_id, not a row of items_units - whose column is unit, not unit_id");
    }
}
