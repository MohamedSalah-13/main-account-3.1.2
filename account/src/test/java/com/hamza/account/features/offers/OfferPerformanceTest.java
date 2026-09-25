package com.hamza.account.features.offers;

import com.hamza.account.features.profitloss.statement.ProfitLossPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.hamza.account.features.offers.OfferEngineTest.d;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The offers' performance report: its figures, its order, and every statement behind it, whole (phase E). */
class OfferPerformanceTest {

    private static final ProfitLossPeriod SEPTEMBER = new ProfitLossPeriod(LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 25));
    private static final ProfitLossPeriod AUGUST = SEPTEMBER.previous(
            com.hamza.account.features.profitloss.statement.ComparisonBasis.PREVIOUS_PERIOD);

    private static OfferFigures figures(int invoices, String covered, String given, String givenBack, String sold,
                                        String returned, String cost) {
        return new OfferFigures(invoices, invoices, null, null, d(covered), d(given), d(givenBack), d(sold),
                d(returned), cost == null ? null : d(cost));
    }

    @Test
    @DisplayName("a row: what stayed given and sold, its times over the offer's group, its change on the period before")
    void aRow() {
        Offer threeFor100 = OfferEngineQuantityTest.quantityPrice(1, "3", "100", null, OfferTarget.item(11));
        OfferPerformanceRow row = new OfferPerformanceRow(threeFor100,
                figures(4, "12", "80", "20", "600", "100", "300"), figures(2, "6", "40", "0", "250", "0", "150"));

        assertEquals(d("60"), row.now().discount(), "80 given less the 20 the returns took back");
        assertEquals(d("500"), row.now().net());
        assertEquals(Optional.of(d("200")), row.now().profit());
        assertEquals(d("4.000"), row.times(), "twelve units covered, three a group");
        assertEquals(Optional.of(d("50.00")), row.discountChange(), "60 against 40");
        assertEquals(Optional.of(d("100.00")), row.netChange(), "500 against 250");
        assertEquals(Optional.empty(), new OfferPerformanceRow(threeFor100, figures(1, "3", "20", "0", "100", "0",
                null), null).netChange(), "nothing before to compare with");
    }

    @Test
    @DisplayName("the report: the most given first, totals summed, a profit only when the cost was read")
    void theReport() {
        Offer first = OfferEngineTest.offer(1, OfferKind.PERCENT, "10", null, OfferTarget.everything());
        Offer second = OfferEngineTest.offer(2, OfferKind.AMOUNT, "5", null, OfferTarget.item(11));
        OfferPerformanceReport withCost = new OfferPerformanceReport(SEPTEMBER, AUGUST, List.of(
                new OfferPerformanceRow(first, figures(1, "5", "10", "0", "90", "0", "50"), null),
                new OfferPerformanceRow(second, figures(3, "9", "45", "0", "300", "0", "200"),
                        figures(1, "2", "10", "0", "80", "0", "60"))), 3, true);

        assertEquals(List.of(2, 1), withCost.rows().stream().map(row -> row.offer().id()).toList());
        assertEquals(d("55"), withCost.discount());
        assertEquals(d("390"), withCost.net());
        assertEquals(Optional.of(d("140")), withCost.profit());
        assertEquals(Optional.of(d("20")), withCost.profitBefore());
        assertEquals(3, withCost.invoices(), "the invoices any offer reached, each once - not 1 + 3");
        assertEquals(Optional.of(d("450.00")), withCost.discountChange(), "55 against 10");

        OfferPerformanceReport without = new OfferPerformanceReport(SEPTEMBER, AUGUST, List.of(
                new OfferPerformanceRow(first, figures(1, "5", "10", "0", "90", "0", null), null)), 1, false);
        assertEquals(Optional.empty(), without.profit());
        assertTrue(new OfferPerformanceReport(SEPTEMBER, AUGUST, null, 0, true).isEmpty());
        assertEquals(LocalDate.of(2026, 8, 1), AUGUST.from(), "the same days of the month before");
        assertEquals(LocalDate.of(2026, 8, 25), AUGUST.to());
    }

    @Test
    @DisplayName("an invoice offer's times are its invoices; a bundle's, its bundles")
    void times() {
        Offer invoice = OfferEngineBundleTest.invoicePercent(1, "1000", "5", OfferTarget.everything());
        assertEquals(d("2.000"), new OfferPerformanceRow(invoice, figures(2, "2.000", "10", "0", "100", "0", null),
                null).times());
        Offer bundle = OfferEngineBundleTest.ramadan(2, "150");
        assertEquals(d("3.000"), new OfferPerformanceRow(bundle, figures(3, "12", "45", "0", "450", "0", null),
                null).times(), "four units a bundle");
        assertEquals(BigDecimal.ZERO, OfferFigures.NONE.net().stripTrailingZeros());
    }

    @Test
    @DisplayName("the figures: each side grouped per offer before the union, a return giving back its units, the cost"
            + " selected only when asked")
    void figuresSql() {
        String sales = "SELECT d.offer_id AS offer_id, COUNT(DISTINCT d.invoice_number) AS invoices, COUNT(*) AS lines_count,"
                + " SUM(d.quantity * d.type_value) AS sold_quantity, 0 AS returned_quantity,"
                + " SUM(d.offer_quantity) AS covered, SUM(d.offer_discount) AS given, 0 AS given_back,"
                + " SUM(d.total_sel_price - d.discount) AS sold, 0 AS returned, SUM(d.total_buy_price) AS cost"
                + " FROM sales d JOIN total_sales h ON h.invoice_number = d.invoice_number"
                + " WHERE h.invoice_date BETWEEN ? AND ? AND d.offer_id IS NOT NULL GROUP BY d.offer_id";
        String returns = "SELECT r.offer_id AS offer_id, 0 AS invoices, 0 AS lines_count, 0 AS sold_quantity,"
                + " SUM(r.quantity * r.type_value) AS returned_quantity, -SUM(r.offer_quantity) AS covered,"
                + " 0 AS given, SUM(r.offer_discount) AS given_back, 0 AS sold,"
                + " SUM(r.total_sel_price - r.discount) AS returned, -SUM(r.total_buy_price) AS cost"
                + " FROM sales_re r JOIN total_sales_re rh ON rh.id = r.invoice_number"
                + " WHERE rh." + com.hamza.account.document.DocumentTableSpec.SALES_RETURN.dateColumn()
                + " BETWEEN ? AND ? AND r.offer_id IS NOT NULL GROUP BY r.offer_id";
        assertEquals("SELECT m.offer_id, SUM(m.invoices) AS invoices, SUM(m.lines_count) AS lines_count,"
                + " SUM(m.sold_quantity) AS sold_quantity, SUM(m.returned_quantity) AS returned_quantity,"
                + " SUM(m.covered) AS covered, SUM(m.given) AS given, SUM(m.given_back) AS given_back,"
                + " SUM(m.sold) AS sold, SUM(m.returned) AS returned, SUM(m.cost) AS cost"
                + " FROM (" + sales + " UNION ALL " + returns + ") m GROUP BY m.offer_id",
                OfferPerformanceQuery.figuresSql(true));
        assertTrue(!OfferPerformanceQuery.figuresSql(false).contains("total_buy_price"),
                "a reader who may not see a profit is not sent a cost");
        assertEquals(OfferPerformanceQuery.FIGURES_PARAMETERS,
                OfferPerformanceQuery.figuresSql(true).chars().filter(c -> c == '?').count());
    }

    @Test
    @DisplayName("the invoices any offer reached, each counted once")
    void invoicesSql() {
        assertEquals("SELECT COUNT(DISTINCT d.invoice_number) FROM sales d JOIN total_sales h"
                + " ON h.invoice_number = d.invoice_number WHERE h.invoice_date BETWEEN ? AND ?"
                + " AND d.offer_id IS NOT NULL", OfferPerformanceQuery.invoicesSql());
    }

    @Test
    @DisplayName("an offer's items: each side grouped per item of that offer, the net most first")
    void itemsSql() {
        String sql = OfferPerformanceQuery.itemsSql(false);
        assertTrue(sql.startsWith("SELECT m.item_id, items.nameItem AS name_item, u.unit_name AS unit_name,"
                + " SUM(m.sold_quantity) AS sold_quantity, SUM(m.returned_quantity) AS returned_quantity,"
                + " SUM(m.given) - SUM(m.given_back) AS discount, SUM(m.sold) - SUM(m.returned) AS net FROM ("), sql);
        assertTrue(sql.contains("SELECT d.num AS item_id,"), sql);
        assertTrue(sql.contains(" AND d.offer_id = ? GROUP BY d.num"), sql);
        assertTrue(sql.contains(" AND r.offer_id = ? GROUP BY r.item_id"), sql);
        assertTrue(sql.endsWith(" JOIN items ON items.id = m.item_id LEFT JOIN units u ON u.unit_id = items.unit_id"
                + " GROUP BY m.item_id, items.nameItem, u.unit_name ORDER BY net DESC, m.item_id"), sql);
        assertEquals(OfferPerformanceQuery.ITEMS_PARAMETERS, sql.chars().filter(c -> c == '?').count());
    }

    @Test
    @DisplayName("an item's stock: the items list's own balance over the named items' rows alone")
    void balancesSql() {
        String sql = OfferQuery.balancesSql(2);
        assertTrue(sql.startsWith("SELECT i.id, i.nameItem, i.mini_quantity, (ip.stock_first_balance"), sql);
        assertTrue(sql.contains("WHERE ist.item_id IN (?, ?)"), sql);
        assertTrue(sql.endsWith(" ip ON ip.item_id = i.id ORDER BY i.id"), sql);
        assertEquals(2, sql.chars().filter(c -> c == '?').count());
    }
}
