package com.hamza.account.features.totals;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotalsSelectionTest {

    @Test
    void countsTheTickedRowsAndAddsWhatTheyComeTo() {
        TotalsSelection selection = TotalsSelection.of(List.of(new BigDecimal("10.10"), new BigDecimal("-2.05")));

        assertEquals(2, selection.count());
        assertEquals(new BigDecimal("8.05"), selection.total());
    }

    @Test
    void nothingTickedIsEmpty() {
        assertTrue(TotalsSelection.of(List.of()).isEmpty());
        assertEquals(TotalsSelection.NONE, TotalsSelection.of(List.of()));
    }

    @Test
    void aRowWithNoTotalIsStillATickedRow() {
        TotalsSelection selection = TotalsSelection.of(Arrays.asList(null, new BigDecimal("3")));

        assertEquals(2, selection.count());
        assertEquals(new BigDecimal("3"), selection.total());
    }
}
