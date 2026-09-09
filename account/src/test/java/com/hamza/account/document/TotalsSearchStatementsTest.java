package com.hamza.account.document;

import com.hamza.account.type.InvoiceType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the three search statements, the way {@code DocumentDaoStatementsTest} pins the rest.
 *
 * <p>A search that names the wrong column still produces valid SQL - it just answers a
 * different question - and these three are built from one shared {@code WHERE}, so the
 * property worth holding is that they keep agreeing with each other.</p>
 */
class TotalsSearchStatementsTest {

    private static final TotalsSearchCriteria EVERYTHING =
            new TotalsSearchCriteria(null, null, null, null, null, null, null, null, null, null);

    @Test
    void aSearchWithNoDatesBoundsNothingAndStillPages() {
        List<Object> params = new ArrayList<>();
        String sql = DocumentTableSpec.SALES.searchPageSql(EVERYTHING, params);

        assertFalse(sql.contains("invoice_date >="), "an absent date must not become a bound");
        assertFalse(sql.contains("invoice_date <="), "an absent date must not become a bound");
        assertTrue(sql.contains("LIMIT ? OFFSET ?"));
        assertTrue(sql.contains("ORDER BY d.invoice_date DESC, d.invoice_number DESC"));
        assertEquals(0, params.size(), "no condition means no bound value");
    }

    @Test
    void eachDateBoundIsAppendedOnItsOwn() {
        List<Object> since = new ArrayList<>();
        String sinceSql = DocumentTableSpec.SALES.searchPageSql(
                withDates(LocalDate.of(2026, 1, 1), null), since);
        assertTrue(sinceSql.contains("AND d.invoice_date >= ?"));
        assertFalse(sinceSql.contains("AND d.invoice_date <= ?"));
        assertEquals(List.of("2026-01-01"), since);

        List<Object> until = new ArrayList<>();
        String untilSql = DocumentTableSpec.SALES.searchPageSql(
                withDates(null, LocalDate.of(2026, 3, 1)), until);
        assertFalse(untilSql.contains("AND d.invoice_date >= ?"));
        assertTrue(untilSql.contains("AND d.invoice_date <= ?"));
        assertEquals(List.of("2026-03-01"), until);
    }

    /**
     * The page is read from the base table, never from the {@code *_names_table} view -
     * that view joins {@code document_profit}, which MySQL materializes whole on every
     * search. See {@link DocumentTableSpec#searchPageSql} for the measurement.
     */
    @Test
    void thePageNeverReadsTheViewAndNeverGroupsInADerivedTable() {
        for (DocumentTableSpec spec : all()) {
            List<Object> params = new ArrayList<>();
            String sql = spec.searchPageSql(EVERYTHING, params);
            assertFalse(sql.contains(spec.view()),
                    spec.type() + ": the page must not read the profit-joining view");
            assertFalse(sql.contains("GROUP BY"),
                    spec.type() + ": a derived table with GROUP BY is materialized whole");
            assertTrue(sql.contains(" FROM " + spec.table() + " d"));
        }
    }

    /** Whatever narrows the page narrows its count and its summary by exactly as much. */
    @Test
    void theThreeStatementsShareOneSetOfConditions() {
        TotalsSearchCriteria criteria = new TotalsSearchCriteria(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 42, "عميل", "مندوب",
                InvoiceType.DEFER, "admin", new BigDecimal("10"), new BigDecimal("99"), "بحث");

        for (DocumentTableSpec spec : all()) {
            List<Object> pageParams = new ArrayList<>();
            List<Object> countParams = new ArrayList<>();
            List<Object> summaryParams = new ArrayList<>();
            spec.searchPageSql(criteria, pageParams);
            spec.searchCountSql(criteria, countParams);
            spec.searchSummarySql(criteria, summaryParams);

            assertEquals(countParams, summaryParams, spec.type() + ": count and summary differ");
            assertEquals(countParams, pageParams,
                    spec.type() + ": the page's conditions differ from its count's");
        }
    }

    /** The page binds its two paging values after every condition, so order matters. */
    @Test
    void theLimitAndOffsetAreBoundAfterTheConditions() {
        List<Object> params = new ArrayList<>();
        String sql = DocumentTableSpec.SALES.searchPageSql(
                withDates(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1)), params);

        assertEquals(2, params.size());
        assertEquals(sql.indexOf("LIMIT ? OFFSET ?"), sql.lastIndexOf("LIMIT ? OFFSET ?"));
        assertTrue(sql.lastIndexOf("AND d.invoice_date <= ?") < sql.indexOf("LIMIT ? OFFSET ?"));
    }

    /** Only the sales families earn a profit, and only the invoice families are paid off later. */
    @Test
    void profitAndLaterPaymentsAppearForExactlyTheFamiliesThatHaveThem() {
        assertTrue(DocumentTableSpec.SALES.hasProfit());
        assertTrue(DocumentTableSpec.SALES_RETURN.hasProfit());
        assertFalse(DocumentTableSpec.PURCHASE.hasProfit());
        assertFalse(DocumentTableSpec.PURCHASE_RETURN.hasProfit());

        assertTrue(DocumentTableSpec.SALES.hasOtherPaid());
        assertTrue(DocumentTableSpec.PURCHASE.hasOtherPaid());
        assertFalse(DocumentTableSpec.SALES_RETURN.hasOtherPaid());
        assertFalse(DocumentTableSpec.PURCHASE_RETURN.hasOtherPaid());

        for (DocumentTableSpec spec : all()) {
            List<Object> params = new ArrayList<>();
            String sql = spec.searchPageSql(EVERYTHING, params);
            assertEquals(spec.hasProfit(), sql.contains("AS total_profit"), spec.type().name());
            assertEquals(spec.hasProfit(), sql.contains("AS profit_percent"), spec.type().name());
            assertEquals(spec.hasOtherPaid(), sql.contains("AS OtherPaid"), spec.type().name());
        }
    }

    /** Every mapper reads these, whichever statement produced the row. */
    @Test
    void thePageProducesTheColumnNamesTheMappersRead() {
        for (DocumentTableSpec spec : all()) {
            List<Object> params = new ArrayList<>();
            String sql = spec.searchPageSql(EVERYTHING, params);
            assertTrue(sql.contains("st.stock_name"), spec.type().name());
            assertTrue(sql.contains("tr.t_name"), spec.type().name());
            assertTrue(sql.contains("us.user_name"), spec.type().name());
            assertEquals(spec.hasDelegate(), sql.contains("em.column_name"), spec.type().name());
            assertTrue(sql.contains("pa.name"), spec.type().name());
        }
    }

    /** The party is joined by hand, so the wrong table would silently search the wrong people. */
    @Test
    void eachFamilyJoinsItsOwnPartyTable() {
        assertEquals("custom", DocumentTableSpec.SALES.partyTable());
        assertEquals("custom", DocumentTableSpec.SALES_RETURN.partyTable());
        assertEquals("suppliers", DocumentTableSpec.PURCHASE.partyTable());
        assertEquals("suppliers", DocumentTableSpec.PURCHASE_RETURN.partyTable());
    }

    private static TotalsSearchCriteria withDates(LocalDate from, LocalDate to) {
        return new TotalsSearchCriteria(from, to, null, null, null, null, null, null, null, null);
    }

    private static List<DocumentTableSpec> all() {
        return List.of(DocumentTableSpec.SALES, DocumentTableSpec.SALES_RETURN,
                DocumentTableSpec.PURCHASE, DocumentTableSpec.PURCHASE_RETURN);
    }
}
