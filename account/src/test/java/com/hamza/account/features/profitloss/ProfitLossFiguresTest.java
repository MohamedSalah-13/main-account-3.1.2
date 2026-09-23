package com.hamza.account.features.profitloss;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfitLossFiguresTest {

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private static ProfitLossRow day(String sales, String cost, String expenses) {
        BigDecimal gross = money(sales).subtract(money(cost));
        return new ProfitLossRow(LocalDate.of(2026, 1, 1), money(sales), money(cost), gross, money(expenses),
                gross.subtract(money(expenses)));
    }

    @Test
    @DisplayName("a month is its days added up - no profit is computed here")
    void aMonthIsItsDaysAddedUp() {
        ProfitLossFigures month = ProfitLossFigures.ZERO.plus(day("1000.00", "700.00", "50.00"))
                .plus(day("500.00", "300.00", "0.00"));

        assertEquals(money("1500.00"), month.netSales());
        assertEquals(money("1000.00"), month.costOfSales());
        assertEquals(money("500.00"), month.grossProfit());
        assertEquals(money("50.00"), month.expenses());
        assertEquals(money("450.00"), month.netProfit());
    }

    @Test
    @DisplayName("the margins are shares of the net sales, to two places")
    void theMargins() {
        ProfitLossFigures month = ProfitLossFigures.ZERO.plus(day("1500.00", "1000.00", "50.00"));

        assertEquals(Optional.of(money("33.33")), month.grossMargin());
        assertEquals(Optional.of(money("30.00")), month.netMargin());
        assertEquals(Optional.of(money("3.33")), month.expenseShare());
    }

    @Test
    @DisplayName("a margin on no sales is absent, not zero")
    void aMarginOnNothingIsAbsent() {
        ProfitLossFigures expensesOnly = ProfitLossFigures.ZERO.plus(day("0.00", "0.00", "40.00"));

        assertEquals(Optional.empty(), expensesOnly.netMargin());
        assertEquals(Optional.empty(), ProfitLossFigures.ZERO.grossMargin());
        assertTrue(expensesOnly.hasActivity(), "a month that only spent still traded");
        assertFalse(ProfitLossFigures.ZERO.hasActivity());
    }

    @Test
    @DisplayName("a month whose returns outweighed its sales has no margin to speak of")
    void negativeNetSalesHaveNoMargin() {
        ProfitLossFigures returned = ProfitLossFigures.ZERO.plus(day("-200.00", "-150.00", "0.00"));

        assertEquals(Optional.empty(), returned.grossMargin());
        assertTrue(returned.hasActivity());
    }

    @Test
    @DisplayName("a change is measured against the size of the figure before it")
    void theChange() {
        assertEquals(Optional.of(money("25.00")), ProfitLossFigures.change(money("125"), money("100")));
        assertEquals(Optional.of(money("-40.00")), ProfitLossFigures.change(money("60"), money("100")));
        assertEquals(Optional.of(money("150.00")), ProfitLossFigures.change(money("50"), money("-100")),
                "a loss of 100 becoming a profit of 50 is a rise");
        assertEquals(Optional.empty(), ProfitLossFigures.change(money("50"), BigDecimal.ZERO),
                "against nothing there is no comparison");
    }

    @Test
    void aMissingFigureIsAZero() {
        ProfitLossFigures month = new ProfitLossFigures(null, null, null, null, null);

        assertEquals(BigDecimal.ZERO, month.netSales());
        assertFalse(month.hasActivity());
    }
}
