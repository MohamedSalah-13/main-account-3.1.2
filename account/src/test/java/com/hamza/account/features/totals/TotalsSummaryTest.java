package com.hamza.account.features.totals;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TotalsSummaryTest {

    @Test
    void calculatesAllDisplayedFiguresInOnePass() {
        List<Row> rows = List.of(
                new Row(100, 10, 90, 50, 25),
                new Row(-20, 0, -20, -5, -4));

        TotalsSummary summary = TotalsSummary.calculate(rows,
                row -> new TotalsSummary.Amounts(
                        row.total(), row.discount(), row.afterDiscount(), row.paid(), row.profit()));

        assertEquals(2, summary.count());
        assertMoney("80.00", summary.total());
        assertMoney("10.00", summary.discount());
        assertMoney("70.00", summary.afterDiscount());
        assertMoney("45.00", summary.paid());
        assertMoney("25.00", summary.remaining());
        assertMoney("21.00", summary.profit());
    }

    @Test
    void anEmptyTableHasACompleteZeroSummary() {
        TotalsSummary summary = TotalsSummary.calculate(List.<Row>of(),
                row -> new TotalsSummary.Amounts(0, 0, 0, 0, 0));

        assertEquals(0, summary.count());
        assertMoney("0.00", summary.total());
        assertMoney("0.00", summary.remaining());
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(new BigDecimal(expected), actual);
    }

    private record Row(double total, double discount, double afterDiscount, double paid, double profit) {
    }
}
