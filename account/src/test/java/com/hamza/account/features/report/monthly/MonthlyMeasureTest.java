package com.hamza.account.features.report.monthly;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.hamza.account.features.report.monthly.MonthFiguresTest.figures;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonthlyMeasureTest {

    private static final MonthFigures MONTH = figures(4, "1000.00", "100.00", 1, "200.00", "20.00");

    @Test
    @DisplayName("each measure reads its own piece of a month")
    void eachMeasureReadsItsPiece() {
        assertEquals(new BigDecimal("720.00"), MonthlyMeasure.NET.of(MONTH));
        assertEquals(new BigDecimal("1000.00"), MonthlyMeasure.GROSS.of(MONTH), "what the old screen called the total");
        assertEquals(new BigDecimal("100.00"), MonthlyMeasure.DISCOUNT.of(MONTH));
        assertEquals(new BigDecimal("180.00"), MonthlyMeasure.RETURNS.of(MONTH));
        assertEquals(BigDecimal.valueOf(4), MonthlyMeasure.INVOICES.of(MONTH));
    }

    @Test
    @DisplayName("only the number of invoices is a count, and the net comes first")
    void onlyTheInvoicesAreACount() {
        assertTrue(MonthlyMeasure.INVOICES.isCount());
        for (MonthlyMeasure measure : MonthlyMeasure.values()) {
            if (measure != MonthlyMeasure.INVOICES) {
                assertFalse(measure.isCount(), measure + " is an amount");
            }
        }
        assertEquals(MonthlyMeasure.NET, MonthlyMeasure.values()[0], "the screen opens on the first");
    }
}
