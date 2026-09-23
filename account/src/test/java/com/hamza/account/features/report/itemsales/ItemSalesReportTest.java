package com.hamza.account.features.report.itemsales;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.hamza.account.features.report.itemsales.ItemSalesRowTest.row;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemSalesReportTest {

    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 31);
    private static final ItemSalesFilter OCTOBER = ItemSalesFilter.of(FROM, TO);

    private static final ItemSalesRow RICE = row(1, "rice", "10", "1", "600", "60", "405");
    private static final ItemSalesRow JUICE = row(2, "juice", "27", "0", "256", "0", "112.5");
    private static final ItemSalesRow OIL = row(3, "oil", "5", "0", "200", "0", "150");

    private static ItemSalesReport october(ItemSalesRow... rows) {
        return new ItemSalesReport(OCTOBER, List.of(rows), Optional.of(new BigDecimal("24")), true);
    }

    @Test
    @DisplayName("the most sold by value first - juice's 27 units come after rice's 9, being worth less")
    void byValueNotByQuantity() {
        ItemSalesReport report = october(OIL, JUICE, RICE);

        assertEquals(List.of(RICE, JUICE, OIL), report.rows());
        assertEquals(1, report.rank(RICE));
        assertEquals(3, report.rank(OIL));
    }

    @Test
    @DisplayName("a tie in value goes to the larger quantity, then to the name")
    void tiesAreBroken() {
        ItemSalesRow a = row(10, "b", "2", "0", "100", "0", null);
        ItemSalesRow b = row(11, "a", "4", "0", "100", "0", null);
        ItemSalesRow c = row(12, "a", "2", "0", "100", "0", null);

        assertEquals(List.of(b, c, a), october(a, b, c).rows());
    }

    @Test
    @DisplayName("the totals are money: sold, returned, net, the margin and its percentage")
    void theTotals() {
        ItemSalesReport report = october(RICE, JUICE, OIL);

        assertEquals(new BigDecimal("1056"), report.sold());
        assertEquals(new BigDecimal("60"), report.returned());
        assertEquals(new BigDecimal("996"), report.net());
        assertEquals(Optional.of(new BigDecimal("328.5")), report.margin());
        assertEquals(Optional.of(new BigDecimal("32.98")), report.marginPercent());
        assertEquals(Optional.of(new BigDecimal("5.68")), report.returnRate());
        assertEquals(Optional.of(new BigDecimal("54.22")), report.share(RICE));
    }

    @Test
    @DisplayName("the items' net less the invoices' own discounts is the invoices' net sales")
    void theInvoicesNet() {
        assertEquals(Optional.of(new BigDecimal("972")), october(RICE, JUICE, OIL).invoicesNet());
    }

    @Test
    @DisplayName("narrowed, the invoices' discounts are left out and there is no invoices' net")
    void aNarrowedReportHasNoInvoicesNet() {
        ItemSalesReport narrowed = new ItemSalesReport(new ItemSalesFilter(FROM, TO, "rice"), List.of(RICE),
                Optional.empty(), true);

        assertTrue(narrowed.invoicesNet().isEmpty());
        assertEquals(Optional.of(new BigDecimal("100.00")), narrowed.share(RICE));
    }

    @Test
    @DisplayName("without the cost there is no margin; with nothing sold, no share and no rate")
    void nothingToDivideBy() {
        ItemSalesReport hidden = new ItemSalesReport(OCTOBER, List.of(row(1, "rice", "1", "0", "10", "0", null)),
                Optional.of(BigDecimal.ZERO), false);
        assertTrue(hidden.margin().isEmpty());
        assertTrue(hidden.marginPercent().isEmpty());

        ItemSalesRow onlyReturned = row(4, "salt", "0", "1", "0", "5", null);
        ItemSalesReport empty = october(onlyReturned);
        assertTrue(empty.share(onlyReturned).isEmpty());
        assertTrue(empty.returnRate().isEmpty());
        assertTrue(empty.top(10).isEmpty(), "an item that only came back is no bar on the chart");
    }

    @Test
    @DisplayName("the chart's bars are the first rows that sold anything, as many as asked")
    void theTop() {
        assertEquals(List.of(RICE, JUICE), october(OIL, JUICE, RICE).top(2));
    }
}
