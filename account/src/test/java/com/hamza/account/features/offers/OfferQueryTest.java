package com.hamza.account.features.offers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every statement over the offers, whole - a lost space is a statement MySQL refuses (V84's lesson). */
class OfferQueryTest {

    private static final String COLUMNS = "o.id, o.name, o.kind, o.status, o.starts_on, o.ends_on, o.weekdays,"
            + " o.priority, o.percent, o.amount, o.offer_price, o.unit_id, o.buy_quantity, o.get_quantity,"
            + " o.get_percent, o.max_per_invoice, o.quantity_limit, o.notes, o.updated_at";

    @Test
    @DisplayName("the till's snapshot, the save's offers for a day, and a document's own")
    void reads() {
        assertEquals("SELECT " + COLUMNS + " FROM offer o WHERE o.status = 'ACTIVE' ORDER BY o.id",
                OfferQuery.ACTIVE_SQL);
        assertEquals("SELECT " + COLUMNS + " FROM offer o WHERE o.status = 'ACTIVE' AND o.starts_on <= ?"
                + " AND (o.ends_on IS NULL OR o.ends_on >= ?) ORDER BY o.id", OfferQuery.IN_FORCE_SQL);
        assertEquals("SELECT DISTINCT s.offer_id FROM sales s WHERE s.invoice_number = ? AND s.offer_id IS NOT NULL",
                OfferQuery.DOCUMENT_OFFERS_SQL);
        assertEquals("SELECT " + COLUMNS + " FROM offer o WHERE o.id IN (?, ?) ORDER BY o.id", OfferQuery.byIdsSql(2));
        assertEquals("SELECT offer_id, price_tier_id FROM offer_price_tier WHERE offer_id IN (?) ORDER BY offer_id,"
                + " price_tier_id", OfferQuery.tiersSql(1));
        assertThrows(IllegalArgumentException.class, () -> OfferQuery.byIdsSql(0));
    }

    @Test
    @DisplayName("the writes, the version compared where the row is written")
    void writes() {
        assertEquals("INSERT INTO offer (name, kind, status, starts_on, ends_on, weekdays, priority, percent, amount,"
                + " offer_price, unit_id, buy_quantity, get_quantity, get_percent, max_per_invoice, quantity_limit,"
                + " notes, user_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                OfferQuery.INSERT_SQL);
        assertEquals("UPDATE offer SET name = ?, kind = ?, starts_on = ?, ends_on = ?, weekdays = ?, priority = ?,"
                + " percent = ?, amount = ?, offer_price = ?, unit_id = ?, buy_quantity = ?, get_quantity = ?,"
                + " get_percent = ?, max_per_invoice = ?, quantity_limit = ?, notes = ?"
                + " WHERE id = ? AND updated_at = ?", OfferQuery.UPDATE_SQL);
        assertEquals("UPDATE offer SET status = ? WHERE id = ? AND updated_at = ?", OfferQuery.STATUS_SQL);
        assertEquals("INSERT INTO offer_target (offer_id, role, scope, item_id, unit_id, sub_group_id, main_group_id,"
                + " excluded) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", OfferQuery.INSERT_TARGET_SQL);
        assertEquals("SELECT (SELECT COUNT(*) FROM sales WHERE offer_id = ?)"
                + " + (SELECT COUNT(*) FROM sales_re WHERE offer_id = ?)", OfferQuery.USED_SQL);
    }

    @Test
    @DisplayName("a global limit: the offers locked in id order, the sales read with a locking read, the returns plainly")
    void limits() {
        assertEquals("SELECT id FROM offer WHERE id IN (?, ?) ORDER BY id FOR UPDATE", OfferQuery.lockLimitedSql(2));
        assertEquals("SELECT s.offer_id, COALESCE(SUM(s.offer_quantity), 0) FROM sales s"
                + " WHERE s.offer_id IN (?, ?) AND s.invoice_number <> ? GROUP BY s.offer_id FOR SHARE",
                OfferQuery.usedOnSalesSql(2, true));
        assertEquals("SELECT s.offer_id, COALESCE(SUM(s.offer_quantity), 0) FROM sales s"
                + " WHERE s.offer_id IN (?) AND s.invoice_number <> ? GROUP BY s.offer_id",
                OfferQuery.usedOnSalesSql(1, false));
        assertEquals("SELECT r.offer_id, COALESCE(SUM(r.offer_quantity), 0) FROM sales_re r"
                + " WHERE r.offer_id IN (?) GROUP BY r.offer_id", OfferQuery.returnedSql(1));
        assertTrue(OfferQuery.USAGE_SQL.contains(" COALESCE(SUM(s.offer_quantity), 0)"
                + " - (SELECT COALESCE(SUM(r.offer_quantity), 0) FROM sales_re r WHERE r.offer_id = ?) AS units"));
        assertEquals(3, OfferQuery.USAGE_SQL.chars().filter(c -> c == '?').count(),
                "the usage binds the offer three times: the returned discount, the returned units, the lines");
    }

    @Test
    @DisplayName("the list: every condition bound, the wildcards in a name taken literally")
    void list() {
        OfferFilter filter = new OfferFilter("10%_off", OfferStatus.ACTIVE, OfferKind.PERCENT,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        OfferQuery.Statement statement = OfferQuery.list(filter);
        assertEquals("SELECT " + COLUMNS + ", un.unit_name,"
                + " (SELECT COUNT(*) FROM offer_target t WHERE t.offer_id = o.id) AS targets,"
                + " (SELECT COUNT(*) FROM sales s WHERE s.offer_id = o.id) AS used_lines,"
                + " (SELECT COALESCE(SUM(s.offer_discount), 0) FROM sales s WHERE s.offer_id = o.id) AS given"
                + " FROM offer o LEFT JOIN units un ON un.unit_id = o.unit_id"
                + " WHERE 1 = 1 AND o.name LIKE ? ESCAPE '!' AND o.status = ? AND o.kind = ?"
                + " AND o.starts_on <= ? AND (o.ends_on IS NULL OR o.ends_on >= ?)"
                + " ORDER BY FIELD(o.status, 'ACTIVE', 'DRAFT', 'STOPPED'), o.starts_on DESC, o.id DESC",
                statement.sql());
        assertEquals(List.of("%10!%!_off%", "ACTIVE", "PERCENT", LocalDate.of(2026, 9, 30),
                LocalDate.of(2026, 9, 1)), statement.parameters());
        assertEquals(List.of(), OfferQuery.list(OfferFilter.everything()).parameters());
    }

    @Test
    @DisplayName("the below-cost candidates: a unit's own price and cost where it has one, the item's times the factor else")
    void candidates() {
        assertTrue(OfferQuery.UNIT_CANDIDATES_SQL.contains("IF(iu.sel_price > 0, iu.sel_price, i.sel_price1 * iu.quantity)"));
        assertTrue(OfferQuery.UNIT_CANDIDATES_SQL.contains("WHERE iu.unit = ? AND iu.unit <> i.unit_id"));
        assertTrue(OfferQuery.UNIT_CANDIDATES_SQL.startsWith(OfferQuery.BASE_CANDIDATES_SQL + " WHERE i.unit_id = ?"));
    }

    @Test
    @DisplayName("the filter counts what the closed panel holds, and refuses days that run backwards")
    void filter() throws Exception {
        assertEquals(0, new OfferFilter("x", OfferStatus.DRAFT, null, null, null).panelConditionCount());
        assertEquals(2, new OfferFilter(null, null, OfferKind.AMOUNT, null, LocalDate.of(2026, 9, 1))
                .panelConditionCount());
        assertEquals(null, new OfferFilter("  ", null, null, null, null).text());
        assertThrows(com.hamza.controlsfx.error.UserValidationException.class, () ->
                OfferFilter.of(null, null, null, LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1)));
    }
}
