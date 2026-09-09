package com.hamza.account.features.totals;

import com.hamza.account.type.InvoiceType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TotalsFilterInputTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);

    @Test
    void trimsOptionalValuesAndBuildsTheDatabaseCriteria() throws Exception {
        var criteria = input(" 42 ", "  عميل  ", " مندوب ", InvoiceType.CASH,
                " admin ", " 10.25 ", " 99.75 ", " ملاحظة ").toCriteria();

        assertEquals(42, criteria.invoiceNumber());
        assertEquals("عميل", criteria.partyName());
        assertEquals("مندوب", criteria.delegateName());
        assertEquals(InvoiceType.CASH, criteria.invoiceType());
        assertEquals("admin", criteria.enteredByUsername());
        assertEquals(new BigDecimal("10.25"), criteria.minTotal());
        assertEquals(new BigDecimal("99.75"), criteria.maxTotal());
        assertEquals("ملاحظة", criteria.freeText());
    }

    @Test
    void blankOptionalValuesDoNotCreateFilters() throws Exception {
        var criteria = input(" ", null, "", null, null, "", " ", "").toCriteria();

        assertNull(criteria.invoiceNumber());
        assertNull(criteria.partyName());
        assertNull(criteria.minTotal());
        assertNull(criteria.freeText());
        assertEquals(0, TotalsFilterInput.hiddenConditionCount(criteria));
    }

    @Test
    void rejectsAnInvertedDateRange() {
        var invalid = new TotalsFilterInput(TO, FROM, null, null, null,
                null, null, null, null, null);

        var error = assertThrows(TotalsFilterInput.InvalidFilterException.class, invalid::toCriteria);
        assertEquals(TotalsFilterInput.Problem.DATE_RANGE, error.problem());
    }

    @Test
    void rejectsNonPositiveInvoiceNumbersAndInvertedTotalRanges() {
        var invoiceError = assertThrows(TotalsFilterInput.InvalidFilterException.class,
                () -> input("0", null, null, null, null, null, null, null).toCriteria());
        assertEquals(TotalsFilterInput.Problem.INVOICE_NUMBER, invoiceError.problem());

        var rangeError = assertThrows(TotalsFilterInput.InvalidFilterException.class,
                () -> input(null, null, null, null, null, "20", "10", null).toCriteria());
        assertEquals(TotalsFilterInput.Problem.TOTAL_RANGE, rangeError.problem());
    }

    @Test
    void countsOnlyConditionsHiddenInsideTheAdvancedPanel() throws Exception {
        var criteria = input("7", "A", "D", InvoiceType.DEFER, "U", "10", "20", "visible").toCriteria();

        assertEquals(6, TotalsFilterInput.hiddenConditionCount(criteria));
    }

    private static TotalsFilterInput input(String invoiceNumber, String party, String delegate,
                                           InvoiceType type, String enteredBy, String min, String max,
                                           String freeText) {
        return new TotalsFilterInput(FROM, TO, invoiceNumber, party, delegate, type,
                enteredBy, min, max, freeText);
    }
}
