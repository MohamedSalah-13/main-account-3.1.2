package com.hamza.account.features.report.monthly;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonthFiguresTest {

    @Test
    @DisplayName("the net is the invoices less their discounts less the returns net of theirs")
    void theNetIsTheProfitAndLossArithmetic() {
        MonthFigures month = figures(3, "1000.00", "100.00", 1, "200.00", "20.00");

        assertEquals(new BigDecimal("180.00"), month.returns(), "a return gives back its total less its discount");
        assertEquals(new BigDecimal("720.00"), month.net(), "1000 - 100 - 180");
    }

    @Test
    @DisplayName("adding two keeps every piece and every count")
    void addingKeepsEveryPiece() {
        MonthFigures sum = figures(2, "500.00", "50.00", 0, "0", "0").plus(figures(1, "300.00", "0", 1, "40.00", "4.00"));

        assertEquals(figures(3, "800.00", "50.00", 1, "40.00", "4.00"), sum);
        assertEquals(new BigDecimal("714.00"), sum.net());
    }

    @Test
    @DisplayName("a missing amount is a zero, and a month with no document is empty whatever it adds to")
    void nullsAndEmptiness() {
        MonthFigures nulls = new MonthFigures(0, null, null, 0, null, null);

        assertEquals(MonthFigures.ZERO, nulls);
        assertTrue(nulls.isEmpty());
        assertFalse(figures(0, "0", "0", 1, "0", "0").isEmpty(), "a return of nothing is still a return");
    }

    static MonthFigures figures(int invoices, String gross, String discount, int returns, String returnsGross,
                                String returnsDiscount) {
        return new MonthFigures(invoices, new BigDecimal(gross), new BigDecimal(discount), returns,
                new BigDecimal(returnsGross), new BigDecimal(returnsDiscount));
    }
}
