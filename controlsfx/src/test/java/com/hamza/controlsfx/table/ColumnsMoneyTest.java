package com.hamza.controlsfx.table;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How a figure of money is written, which had no single answer before.
 * <p>
 * Screens printed amounts with {@code String.valueOf(double)}, so a balance that had been through
 * arithmetic showed as {@code 1234.5600000000002}, and the same figure appeared with a thousands
 * separator on one screen of a ledger and without on another. This is the one definition; a test on
 * it is cheap and it is the kind of thing that drifts back.
 */
class ColumnsMoneyTest {

    @Test
    @DisplayName("two decimals, always, and a thousands separator")
    void formatsAsMoney() {
        assertEquals("1,234.56", Columns.money(new BigDecimal("1234.56")));
        assertEquals("1,000,000.00", Columns.money(new BigDecimal("1000000")));
        assertEquals("0.00", Columns.money(BigDecimal.ZERO));
        assertEquals("7.00", Columns.money(new BigDecimal("7")));
    }

    /** The artefact that made this necessary: a double that has been through arithmetic. */
    @Test
    void roundsAwayFloatingPointNoise() {
        assertEquals("1,234.56", Columns.money(BigDecimal.valueOf(1234.5600000000002)));
        assertEquals("0.10", Columns.money(BigDecimal.valueOf(0.1 + 0.2 - 0.2)));
    }

    /** Half up, which is what a person doing it with a pen does. */
    @Test
    void roundsHalfUp() {
        assertEquals("2.35", Columns.money(new BigDecimal("2.345")));
        assertEquals("-2.35", Columns.money(new BigDecimal("-2.345")));
    }

    @Test
    void keepsTheSignOfANegative() {
        assertEquals("-1,234.56", Columns.money(new BigDecimal("-1234.56")));
    }

    /**
     * An empty cell stays empty rather than printing {@code 0.00}.
     * <p>
     * A row with nothing in a column is not a row with zero in it - on a statement, a movement with
     * no debit and a movement debiting nothing are different things to read.
     */
    @Test
    void nothingIsNotZero() {
        assertEquals("", Columns.money(null));
    }
}
