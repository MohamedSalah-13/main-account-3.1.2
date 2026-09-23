package com.hamza.account.features.profitloss.yearly;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MonthBreakdownTest {

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    @Test
    @DisplayName("the view's eight columns fold into gross, discount, returns and net purchases")
    void fromTheView() {
        MonthBreakdown month = MonthBreakdown.fromView(3,
                money("1000.00"), money("100.00"),      // sales, their discount
                money("200.00"), money("20.00"),        // sales returns, their discount
                money("800.00"), money("50.00"),        // purchases, their discount
                money("100.00"), money("10.00"));       // purchase returns, their discount

        assertEquals(money("1000.00"), month.grossSales());
        assertEquals(money("100.00"), month.salesDiscount());
        assertEquals(money("180.00"), month.salesReturns(), "a return is net of its own discount");
        assertEquals(money("660.00"), month.netPurchases(), "800 - 50 - (100 - 10)");
        assertEquals(money("720.00"), month.netSales(), "1000 - 100 - 180");
    }

    @Test
    @DisplayName("what the view leaves null is a zero")
    void nullsAreZeros() {
        MonthBreakdown month = MonthBreakdown.fromView(1, null, null, null, null, null, null, null, null);

        assertEquals(BigDecimal.ZERO, month.netSales());
        assertEquals(BigDecimal.ZERO, month.netPurchases());
    }

    @Test
    void aMonthIsOneToTwelve() {
        assertThrows(IllegalArgumentException.class, () -> MonthBreakdown.empty(0));
        assertThrows(IllegalArgumentException.class, () -> MonthBreakdown.empty(13));
    }
}
