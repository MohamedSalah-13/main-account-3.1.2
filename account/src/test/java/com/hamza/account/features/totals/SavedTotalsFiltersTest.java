package com.hamza.account.features.totals;

import com.hamza.account.document.TotalsSearchCriteria;
import com.hamza.account.type.InvoiceType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SavedTotalsFiltersTest {

    @Test
    void roundTripsEveryStandingConditionButNotTheTemporarySearchText() {
        var original = new TotalsSearchCriteria(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                42,
                "عميل; رئيسي=1",
                "مندوب",
                InvoiceType.DEFER,
                "admin",
                new BigDecimal("10.25"),
                new BigDecimal("99.75"),
                "بحث مؤقت");

        TotalsSearchCriteria restored = SavedTotalsFilters.decode(SavedTotalsFilters.encode(original));

        assertEquals(original.dateFrom(), restored.dateFrom());
        assertEquals(original.dateTo(), restored.dateTo());
        assertEquals(original.invoiceNumber(), restored.invoiceNumber());
        assertEquals(original.partyName(), restored.partyName());
        assertEquals(original.delegateName(), restored.delegateName());
        assertEquals(original.invoiceType(), restored.invoiceType());
        assertEquals(original.enteredByUsername(), restored.enteredByUsername());
        assertEquals(original.minTotal(), restored.minTotal());
        assertEquals(original.maxTotal(), restored.maxTotal());
        assertNull(restored.freeText());
    }

    @Test
    void unreadableEntryIsIgnoredInsteadOfBreakingTheSavedList() {
        assertNull(SavedTotalsFilters.decode("from=not-a-date;to=2026-01-01;"));
    }
}
