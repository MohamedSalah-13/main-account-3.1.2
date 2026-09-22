package com.hamza.account.features.shift;

import com.hamza.account.model.domain.ShiftSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShiftScreenSummaryTest {

    /** Opening 500, in 1,390, out 170: expected 1,720. */
    private static ShiftSummary summary() {
        return ShiftSummary.builder()
                .openBalance(new BigDecimal("500"))
                .totalSales(new BigDecimal("1250"))
                .totalSalesReturns(new BigDecimal("75"))
                .totalExpenses(new BigDecimal("60"))
                .otherIn(new BigDecimal("140"))
                .otherOut(new BigDecimal("35"))
                .totalIn(new BigDecimal("1390"))
                .totalOut(new BigDecimal("170"))
                .invoicesCount(12)
                .build();
    }

    @Test
    void anOrdinaryCloseShowsEveryFigureAsTheColumnsWriteIt() {
        ShiftScreenSummary view = ShiftScreenSummary.of(summary(), new BigDecimal("1700"), false);

        assertEquals("1,250.00", view.sales());
        assertEquals("75.00", view.returns());
        assertEquals("60.00", view.expenses());
        assertEquals("140.00", view.otherIn());
        assertEquals("35.00", view.otherOut());
        assertEquals("1,720.00", view.expected());
        assertEquals("-20.00", view.difference());
        assertEquals("12", view.invoices());
        assertEquals(ShiftScreenSummary.Tone.SHORT, view.tone());
    }

    /**
     * The screen used to hide the expected balance and the difference and show the five movement
     * figures that add up to them - so the number being kept from the cashier was one addition away,
     * on the screen they count in front of.
     */
    @Test
    void aBlindCloseWithholdsEveryAmount() {
        ShiftScreenSummary view = ShiftScreenSummary.of(summary(), new BigDecimal("1700"), true);

        List<String> amounts = List.of(view.sales(), view.returns(), view.expenses(), view.otherIn(),
                view.otherOut(), view.expected(), view.difference());
        assertTrue(amounts.stream().allMatch(ShiftScreenSummary.ABSENT::equals), amounts.toString());
        assertEquals("12", view.invoices(), "how many invoices were rung up is not an amount");
        assertEquals(ShiftScreenSummary.Tone.NONE, view.tone(), "and nothing is coloured short or over");
    }

    @Test
    void aDrawerThatAgreesOrHoldsMoreReadsAsSuch() {
        assertEquals(ShiftScreenSummary.Tone.BALANCED,
                ShiftScreenSummary.of(summary(), new BigDecimal("1720"), false).tone());
        assertEquals(ShiftScreenSummary.Tone.OVER,
                ShiftScreenSummary.of(summary(), new BigDecimal("1750"), false).tone());
    }

    @Test
    void noOpenShiftIsEveryFigureAbsent() {
        ShiftScreenSummary view = ShiftScreenSummary.of(null, BigDecimal.ZERO, false);

        assertEquals(ShiftScreenSummary.none(), view);
        assertEquals(ShiftScreenSummary.ABSENT, view.invoices());
        assertEquals(ShiftScreenSummary.Tone.NONE, view.tone());
    }
}
