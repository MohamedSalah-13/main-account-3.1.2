package com.hamza.account.features.treasury.statement;

import com.hamza.account.features.currency.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which figures a statement shows. The row is ForeignTreasuryDatabaseAcceptanceTest's purchase of
 * dollars: 100 dollars into the drawer, 4,900 in the books, with the drawer then holding 180 dollars
 * at a book value of 8,600.
 */
class TreasuryStatementCurrencyTest {

    private static final Currency DOLLAR = new Currency(2, "USD", "دولار أمريكي", "$", "", 2, false, true, 2);

    private static final TreasuryStatementRow PURCHASE = new TreasuryStatementRow(
            12, LocalDate.of(2026, 9, 23), null, TreasuryMovementKind.TRANSFER_IN, "تحويل وارد", 7, "درج الدولار",
            new BigDecimal("4900"), BigDecimal.ZERO, new BigDecimal("8600"),
            new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("180"), 9, "operator");

    private static final TreasuryStatementTotals TOTALS = new TreasuryStatementTotals(
            new TreasuryStatementSummary(new BigDecimal("4800"), new BigDecimal("7300"),
                    new BigDecimal("2560"), new BigDecimal("9540")),
            new TreasuryStatementSummary(new BigDecimal("100"), new BigDecimal("150"),
                    new BigDecimal("50"), new BigDecimal("200")));

    @Test
    @DisplayName("a foreign treasury's statement reads its own figures, row and totals alike")
    void aForeignStatementReadsTheOwnFigures() {
        TreasuryStatementCurrency dollars = new TreasuryStatementCurrency(DOLLAR);

        assertTrue(dollars.isForeign());
        assertEquals("USD", dollars.code());
        assertEquals(new BigDecimal("100"), dollars.income(PURCHASE));
        assertEquals(BigDecimal.ZERO, dollars.output(PURCHASE));
        assertEquals(new BigDecimal("180"), dollars.runningBalance(PURCHASE));
        assertSame(TOTALS.own(), dollars.summary(TOTALS));
    }

    @Test
    @DisplayName("every other statement reads the books' figures and names no currency")
    void theBaseReadsTheBooks() {
        TreasuryStatementCurrency base = TreasuryStatementCurrency.BASE;

        assertFalse(base.isForeign());
        assertEquals("", base.code());
        assertEquals(new BigDecimal("4900"), base.income(PURCHASE));
        assertEquals(new BigDecimal("8600"), base.runningBalance(PURCHASE));
        assertSame(TOTALS.base(), base.summary(TOTALS));
    }

    @Test
    @DisplayName("a row of a treasury in the base carries its figures as its own")
    void aBaseRowIsItsOwn() {
        TreasuryStatementRow sale = new TreasuryStatementRow(3, LocalDate.of(2026, 9, 23), null,
                TreasuryMovementKind.SALES, "المبيعات", 1, "الرئيسية", new BigDecimal("250"), BigDecimal.ZERO,
                new BigDecimal("1250"), 9, "operator");

        assertEquals(sale.income(), sale.incomeOwn());
        assertEquals(sale.output(), sale.outputOwn());
        assertEquals(sale.runningBalance(), sale.runningBalanceOwn());
        assertSame(TOTALS.base(), TreasuryStatementTotals.inBase(TOTALS.base()).own());
    }
}
