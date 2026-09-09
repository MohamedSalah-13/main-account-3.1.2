package com.hamza.account.features.totals;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.TotalsSearchCriteria;
import com.hamza.account.type.InvoiceType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotalsReportTest {

    private static final TotalsSearchCriteria EVERYTHING =
            new TotalsSearchCriteria(null, null, null, null, null, null, null, null, null, null);

    // ---- what a report is run over -------------------------------------------------

    /**
     * The property the whole arrangement rests on: a printed summary describes exactly the
     * documents the list matched, because both are built from the same conditions.
     */
    @Test
    void everyReportBindsTheSameValuesAsThePageItIsPrintedFrom() {
        TotalsSearchCriteria criteria = new TotalsSearchCriteria(
                LocalDate.of(2026, 1, 1), null, null, "عميل", "مندوب",
                InvoiceType.CASH, "admin", new BigDecimal("5"), null, "ملاحظة");

        List<Object> pageParams = new ArrayList<>();
        DocumentTableSpec.SALES.searchPageSql(criteria, pageParams);

        for (DocumentTableSpec.Report report : DocumentTableSpec.Report.values()) {
            List<Object> reportParams = new ArrayList<>();
            DocumentTableSpec.SALES.reportSql(report, criteria, reportParams);
            assertEquals(pageParams, reportParams, report + " asks a different question");
        }
    }

    @Test
    void aPurchaseHasNoDelegateReportAndSayingSoIsAnError() {
        assertFalse(DocumentTableSpec.PURCHASE.supports(DocumentTableSpec.Report.BY_DELEGATE));
        assertTrue(DocumentTableSpec.SALES.supports(DocumentTableSpec.Report.BY_DELEGATE));
        assertThrows(IllegalArgumentException.class, () -> DocumentTableSpec.PURCHASE
                .reportSql(DocumentTableSpec.Report.BY_DELEGATE, EVERYTHING, new ArrayList<>()));
    }

    @Test
    void everyReportIsBoundedSoNoGroupingCanRunAway() {
        for (DocumentTableSpec.Report report : DocumentTableSpec.Report.values()) {
            String sql = DocumentTableSpec.SALES.reportSql(report, EVERYTHING, new ArrayList<>());
            assertTrue(sql.contains("LIMIT " + DocumentTableSpec.REPORT_ROW_LIMIT), report.name());
            assertTrue(sql.contains("GROUP BY"), report.name());
        }
    }

    /** The per-item report reads the lines, so its joins must precede the conditions. */
    @Test
    void theItemReportJoinsItsLinesBeforeTheWhere() {
        String sql = DocumentTableSpec.SALES.reportSql(
                DocumentTableSpec.Report.BY_ITEM, EVERYTHING, new ArrayList<>());
        assertTrue(sql.indexOf("JOIN sales ln") < sql.indexOf("WHERE"));
        assertTrue(sql.indexOf("JOIN items it") < sql.indexOf("WHERE"));
        assertFalse(sql.contains("sum_profit"), "a line has no share of an invoice discount");
    }

    // ---- what a report says about itself --------------------------------------------

    /** A page that does not say what it counted cannot be argued with afterwards. */
    @Test
    void theDescriptionNamesEveryActiveCondition() {
        TotalsSearchCriteria criteria = new TotalsSearchCriteria(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), 7, "احمد حامد", "مندوب",
                InvoiceType.DEFER, "admin", new BigDecimal("10"), new BigDecimal("90"), "دفعة");

        String description = TotalsFilterDescription.describe(criteria, TotalsReportTest::echo);

        assertTrue(description.contains("period[2026-01-01,2026-03-31]"));
        assertTrue(description.contains("party[احمد حامد]"));
        assertTrue(description.contains("delegate[مندوب]"));
        assertTrue(description.contains("entered.by[admin]"));
        assertTrue(description.contains("invoice.number[7]"));
        assertTrue(description.contains("payment.type["));
        assertTrue(description.contains("total.range[10,90]"));
        assertTrue(description.contains("text[دفعة]"));
    }

    @Test
    void anUnboundedSearchStillSaysSoRatherThanSayingNothing() {
        String description = TotalsFilterDescription.describe(EVERYTHING, TotalsReportTest::echo);
        assertEquals("all.dates[]", description);
    }

    @Test
    void oneOpenEndAndOneBoundAreDescribedApart() {
        assertTrue(TotalsFilterDescription.describe(
                new TotalsSearchCriteria(LocalDate.of(2026, 5, 1), null, null, null, null,
                        null, null, null, null, null), TotalsReportTest::echo)
                .contains("since[2026-05-01]"));
        assertTrue(TotalsFilterDescription.describe(
                new TotalsSearchCriteria(null, LocalDate.of(2026, 5, 1), null, null, null,
                        null, null, null, null, null), TotalsReportTest::echo)
                .contains("until[2026-05-01]"));
        assertTrue(TotalsFilterDescription.describe(
                new TotalsSearchCriteria(null, null, null, null, null, null, null,
                        new BigDecimal("50"), null, null), TotalsReportTest::echo)
                .contains("total.min[50]"));
    }

    // ---- what a report looks like ----------------------------------------------------

    @Test
    void aPurchaseSummaryPrintsNoProfitColumn() {
        var sales = layout(true, false, DocumentTableSpec.Report.BY_PARTY);
        var purchase = layout(false, false, DocumentTableSpec.Report.BY_PARTY);

        assertEquals(8, sales.headers().length);
        assertEquals(7, purchase.headers().length);
        assertEquals(sales.headers().length, sales.columnWidths().length);
        assertEquals(purchase.headers().length, purchase.rows().getFirst().length);
        // every column carries its own total, not just the last one
        assertEquals(sales.headers().length, sales.totals().length);
        assertEquals(purchase.headers().length, purchase.totals().length);
    }

    @Test
    void theItemSummaryCountsQuantityInsteadOfCash() {
        var layout = layout(true, true, DocumentTableSpec.Report.BY_ITEM);

        assertEquals(4, layout.headers().length);
        assertEquals("2.5", layout.rows().getFirst()[2]);
        assertEquals("120.00", layout.rows().getFirst()[3]);
    }

    @Test
    void everyRowHasExactlyOneCellPerHeader() {
        for (DocumentTableSpec.Report report : DocumentTableSpec.Report.values()) {
            boolean perItem = report == DocumentTableSpec.Report.BY_ITEM;
            var layout = layout(true, perItem, report);
            for (String[] row : layout.rows()) {
                assertEquals(layout.headers().length, row.length, report.name());
            }
            assertEquals(layout.headers().length, layout.totals().length, report.name());
        }
    }

    // ---- the invoice-by-invoice listing ---------------------------------------------

    @Test
    void theDocumentListingCarriesOneLinePerInvoiceAndATotalPerColumn() {
        var layout = documentLayout(true);

        assertEquals(10, layout.headers().length);
        assertEquals(2, layout.rows().size());
        assertEquals(layout.headers().length, layout.rows().getFirst().length);
        assertEquals(layout.headers().length, layout.totals().length);
        assertEquals(layout.headers().length, layout.columnWidths().length);
    }

    /** The line under a listing has to be the listing summed, or it is worse than absent. */
    @Test
    void theListingTotalIsTheSumOfItsOwnLines() {
        var layout = documentLayout(true);
        String[] totals = layout.totals();

        assertEquals("2", totals[3], "how many documents were listed");
        assertEquals("300.00", totals[4]);   // 100 + 200
        assertEquals("30.00", totals[5]);    // 10 + 20
        assertEquals("270.00", totals[6]);   // net
        assertEquals("120.00", totals[7]);   // paid
        assertEquals("150.00", totals[8]);   // still owed
        assertEquals("75.00", totals[9]);    // profit
    }

    @Test
    void aPurchaseListingHasNoProfitColumnEither() {
        assertEquals(9, documentLayout(false).headers().length);
        assertEquals(9, documentLayout(false).totals().length);
    }

    private static TotalsReportLayout documentLayout(boolean hasProfit) {
        var first = new TotalsDocumentRow(1, "2026-01-01", "عميل", "نقدي",
                new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("40"),
                hasProfit ? new BigDecimal("25") : BigDecimal.ZERO);
        var second = new TotalsDocumentRow(2, "2026-01-02", "عميل آخر", "آجل",
                new BigDecimal("200"), new BigDecimal("20"), new BigDecimal("80"),
                hasProfit ? new BigDecimal("50") : BigDecimal.ZERO);
        return TotalsReportLayout.ofDocuments(List.of(first, second), hasProfit, key -> key);
    }

    private static TotalsReportLayout layout(boolean hasProfit, boolean perItem,
                                             DocumentTableSpec.Report kind) {
        TotalsReportRow row = new TotalsReportRow("صنف", 3, new BigDecimal("2.500"),
                new BigDecimal("120"), new BigDecimal("20"), new BigDecimal("60"),
                new BigDecimal("35"));
        var report = new TotalsReportService.TotalsReport(
                kind, List.of(row), row, hasProfit, perItem, false);
        return TotalsReportLayout.of(report, kind, key -> key);
    }

    /** Echoes the key and its arguments so a test can see which was used, not its wording. */
    private static String echo(String key, Object[] args) {
        String name = key.replace("invoice.report.filter.", "");
        if (name.equals("separator")) return " | ";
        StringBuilder text = new StringBuilder(name).append('[');
        for (int i = 0; i < args.length; i++) {
            if (i > 0) text.append(',');
            text.append(args[i]);
        }
        return text.append(']').toString();
    }
}
