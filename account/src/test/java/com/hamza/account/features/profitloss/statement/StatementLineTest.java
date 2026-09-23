package com.hamza.account.features.profitloss.statement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatementLineTest {

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    @Test
    @DisplayName("more spent is a rise, though an expense is written negative")
    void aDeductionIsComparedBySize() {
        StatementLine expenses = StatementLine.subtotal("profitloss.line.expenses.total", money("-4400"), money("-4000"));
        assertEquals(Optional.of(money("10.00")), expenses.change());
    }

    @Test
    @DisplayName("a result is compared signed, so a loss that shrinks is a rise")
    void aResultIsComparedSigned() {
        StatementLine loss = StatementLine.result("profitloss.net.profit", money("-50"), money("-100"));
        assertEquals(Optional.of(money("50.00")), loss.change());
    }

    @Test
    @DisplayName("across a change of sign there is no size to compare, and the signed change is the answer")
    void oppositeSigns() {
        StatementLine unexplained = StatementLine.item("profitloss.line.unexplained", money("20"), money("-10"));
        assertEquals(Optional.of(money("300.00")), unexplained.change());
    }

    @Test
    void aHeadingHasNoFigureAndNoChange() {
        StatementLine heading = StatementLine.heading("profitloss.section.revenue");
        assertTrue(heading.isHeading());
        assertTrue(heading.change().isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> new StatementLine(StatementLine.Kind.ITEM, "x", null, null, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
                () -> new StatementLine(StatementLine.Kind.ITEM, null, null, BigDecimal.ONE, BigDecimal.ONE));
    }

    @Test
    void nothingToCompareWith() {
        assertTrue(StatementLine.item("profitloss.line.returns", money("-5"), BigDecimal.ZERO).change().isEmpty());
    }
}
