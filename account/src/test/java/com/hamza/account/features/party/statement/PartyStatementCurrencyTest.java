package com.hamza.account.features.party.statement;

import com.hamza.account.features.currency.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which of a statement's two sets of figures it shows (docs/currency-plan.md §14 ق-ج٨): a dollar
 * customer's statement is in dollars, with each movement's book value beside it.
 */
class PartyStatementCurrencyTest {

    private static final Currency USD = new Currency(3, "USD", "دولار أمريكي", "$", "", 2, false, true, 3);
    private static final Currency KWD = new Currency(4, "KWD", "دينار كويتي", "د.ك", "", 3, false, true, 4);

    /** A sale of 1,000 dollars at 48, then its payment in full at 50. */
    private static final PartyStatementRow SALE = row(PartyMovementKind.INVOICE, "48000", "0", "48000",
            "1000", "0", "1000");
    private static final PartyStatementRow PAYMENT = row(PartyMovementKind.PAYMENT, "0", "50000", "-2000",
            "0", "1000", "0");

    private static PartyStatementRow row(PartyMovementKind kind, String purchase, String paid, String running,
                                         String purchaseOwn, String paidOwn, String runningOwn) {
        return new PartyStatementRow(1, LocalDate.of(2026, 9, 1), LocalDateTime.of(2026, 9, 1, 10, 0), kind,
                false, 1, new BigDecimal(purchase), BigDecimal.ZERO, new BigDecimal(paid), new BigDecimal(running),
                0, "", 0, "", "", new BigDecimal(purchaseOwn), BigDecimal.ZERO, new BigDecimal(paidOwn),
                new BigDecimal(runningOwn));
    }

    @Test
    @DisplayName("a statement in the base shows the rows as they are")
    void inTheBase() {
        assertFalse(PartyStatementCurrency.BASE.isForeign());
        assertSame(SALE, PartyStatementCurrency.BASE.shown(SALE));
        assertEquals("", PartyStatementCurrency.BASE.code());
    }

    @Test
    @DisplayName("a dollar statement shows dollars: paid in full is nothing owed, whatever the rates did")
    void inDollars() {
        PartyStatementCurrency dollars = new PartyStatementCurrency(USD);
        List<PartyStatementRow> shown = dollars.shown(List.of(SALE, PAYMENT));

        assertEquals(0, new BigDecimal("1000").compareTo(shown.get(0).debit()));
        assertEquals(0, new BigDecimal("1000").compareTo(shown.get(1).credit()));
        assertEquals(0, shown.get(1).runningBalance().signum(), "a thousand dollars paid for a thousand owed");
        assertEquals("USD", dollars.code());
    }

    @Test
    @DisplayName("the book value is the movement in the books - where the rates' difference shows")
    void theBookValue() {
        PartyStatementCurrency dollars = new PartyStatementCurrency(USD);
        assertEquals(0, new BigDecimal("48000").compareTo(dollars.bookValue(SALE)));
        assertEquals(0, new BigDecimal("-50000").compareTo(dollars.bookValue(PAYMENT)));
        assertEquals(0, new BigDecimal("-2000").compareTo(PAYMENT.runningBalance()),
                "the books say -2,000 where the customer owes nothing: shown, never posted");
    }

    @Test
    @DisplayName("a row in the base is its own figures in its own currency")
    void aBaseRowIsItsOwn() {
        PartyStatementRow base = new PartyStatementRow(1, LocalDate.of(2026, 9, 1), null,
                PartyMovementKind.PAYMENT, false, 1, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("300"),
                new BigDecimal("700"), 0, "", 0, "", "");
        assertEquals(base.paid(), base.paidOwn());
        assertEquals(base.runningBalance(), base.runningBalanceOwn());
    }

    @Test
    @DisplayName("a dinar keeps its third place on the statement")
    void threePlaces() {
        PartyStatementRow dinars = row(PartyMovementKind.PAYMENT, "0", "200.80", "0", "0", "1.255", "-1.255");
        PartyStatementRow shown = new PartyStatementCurrency(KWD).shown(dinars);
        assertEquals(new BigDecimal("1.255"), shown.credit());
        assertEquals(new BigDecimal("-1.255"), shown.runningBalance());
    }

    @Test
    @DisplayName("the totals shown are the currency's own set")
    void theTotals() {
        PartyStatementSummary base = new PartyStatementSummary(BigDecimal.ZERO, new BigDecimal("48000"),
                new BigDecimal("50000"), new BigDecimal("-2000"));
        PartyStatementSummary own = new PartyStatementSummary(BigDecimal.ZERO, new BigDecimal("1000"),
                new BigDecimal("1000"), BigDecimal.ZERO);
        PartyStatementTotals totals = new PartyStatementTotals(base, own);
        assertSame(own, new PartyStatementCurrency(USD).summary(totals));
        assertSame(base, PartyStatementCurrency.BASE.summary(totals));
        assertTrue(own.rowsExplainTheBalance());
    }
}
