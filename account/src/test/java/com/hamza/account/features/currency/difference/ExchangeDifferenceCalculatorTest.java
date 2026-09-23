package com.hamza.account.features.currency.difference;

import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.currency.RateInForce;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A period's differences, worked out by hand (docs/currency-plan.md §16 ق-هـ٢ to ق-هـ٦). */
class ExchangeDifferenceCalculatorTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);
    private static final Currency USD = new Currency(2, "USD", "دولار", "$", "", 2, false, true, 1);
    private static final Currency SAR = new Currency(3, "SAR", "ريال", "ر.س", "", 2, false, true, 2);
    private static final Map<Integer, Currency> CURRENCIES = Map.of(2, USD, 3, SAR);

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, d(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }

    private static Map<Integer, RateInForce> rates(String usd) {
        return usd == null ? Map.of() : Map.of(2, new RateInForce(2, FROM, d(usd), null));
    }

    private static ExchangeMovement move(ExchangeAccountKind kind, int id, LocalDate date, int source,
                                         String own, String book) {
        return new ExchangeMovement(kind, id, date, source, 1, d(own), d(book));
    }

    @Test
    @DisplayName("a dollar supplier paid in part at 49 and valued at 50: -400 realized, -1,200 unrealized")
    void supplierPaidInPart() {
        ExchangeAccount supplier = new ExchangeAccount(ExchangeAccountKind.SUPPLIER, 7, "Acme", 2);
        List<ExchangeMovement> movements = List.of(
                move(ExchangeAccountKind.SUPPLIER, 7, LocalDate.of(2026, 9, 5), 3, "1000", "48000"),
                move(ExchangeAccountKind.SUPPLIER, 7, LocalDate.of(2026, 9, 20), 2, "-400", "-19600"));

        ExchangeDifferenceReport report = ExchangeDifferenceCalculator.report(FROM, TO, List.of(supplier),
                movements, CURRENCIES, rates("47"), rates("50"));
        ExchangeDifferenceRow row = report.rows().get(0);

        assertMoney("600", row.ownEnd());
        assertMoney("28400", row.bookEnd());
        assertMoney("48", row.averageEnd());
        assertMoney("30000", row.valueEnd());
        // Paid 49 for dollars owed at 48: a loss of 400 on the four hundred.
        assertMoney("-400", row.realizedPeriod());
        assertMoney("0", row.unrealizedStart());
        assertMoney("-1200", row.unrealizedEnd());
        assertMoney("-1600", row.result());
        // Owing 600 dollars worth 30,000 against a book value of 28,400.
        assertMoney("-1600", row.totalEnd());
        assertEquals(2, row.lines().size());
        assertMoney("0", row.lines().get(0).realized());
        assertMoney("-400", row.lines().get(1).realized());
        assertMoney("49", row.lines().get(1).rate());
    }

    @Test
    @DisplayName("a balance brought into the period is valued on the day before it, and its sale realizes in it")
    void broughtForward() {
        ExchangeAccount drawer = new ExchangeAccount(ExchangeAccountKind.TREASURY, 4, "Dollars", 2);
        List<ExchangeMovement> movements = List.of(
                move(ExchangeAccountKind.TREASURY, 4, LocalDate.of(2026, 8, 10), 0, "100", "4800"),
                move(ExchangeAccountKind.TREASURY, 4, LocalDate.of(2026, 9, 15), 11, "-100", "-5000"));

        ExchangeDifferenceRow row = ExchangeDifferenceCalculator.report(FROM, TO, List.of(drawer), movements,
                CURRENCIES, rates("49"), Map.of()).rows().get(0);

        assertMoney("100", row.ownStart());
        assertMoney("4800", row.bookStart());
        assertMoney("100", row.unrealizedStart());
        assertMoney("200", row.realizedPeriod());
        // Nothing held at the end: worth nothing at any rate, so the missing rate for the 30th does not matter.
        assertMoney("0", row.unrealizedEnd());
        assertTrue(row.valued());
        assertMoney("-100", row.unrealizedChange());
        assertMoney("100", row.result());
        assertEquals(1, row.lines().size(), "the opening is brought forward, not listed");
    }

    @Test
    @DisplayName("a balance held on a day with no rate has no unrealized figure, and the totals say so")
    void noRateIsNoFigure() {
        ExchangeAccount customer = new ExchangeAccount(ExchangeAccountKind.CUSTOMER, 9, "Zed", 2);
        List<ExchangeMovement> movements = List.of(
                move(ExchangeAccountKind.CUSTOMER, 9, LocalDate.of(2026, 9, 2), 3, "100", "4800"),
                move(ExchangeAccountKind.CUSTOMER, 9, LocalDate.of(2026, 9, 3), 2, "-50", "-2450"));

        ExchangeDifferenceReport report = ExchangeDifferenceCalculator.report(FROM, TO, List.of(customer),
                movements, CURRENCIES, Map.of(), Map.of());
        ExchangeDifferenceRow row = report.rows().get(0);

        assertMoney("50", row.realizedPeriod());
        assertNull(row.valueEnd());
        assertNull(row.unrealizedEnd());
        assertNull(row.result());
        assertNull(row.totalEnd());
        assertFalse(row.valued());
        ExchangeDifferenceSummary summary = report.summary();
        assertMoney("50", summary.realized());
        assertMoney("0", summary.unrealizedChange());
        assertEquals(1, summary.accountsWithoutRate());
        assertEquals(List.of("USD"), summary.currenciesWithoutRate());
    }

    @Test
    @DisplayName("the totals are the rows' sums, the lines add up to the realized, and rows are listed by kind")
    void totalsAndOrder() {
        ExchangeAccount supplier = new ExchangeAccount(ExchangeAccountKind.SUPPLIER, 1, "A supplier", 2);
        ExchangeAccount customer = new ExchangeAccount(ExchangeAccountKind.CUSTOMER, 1, "A customer", 2);
        ExchangeAccount drawer = new ExchangeAccount(ExchangeAccountKind.TREASURY, 1, "Z drawer", 2);
        List<ExchangeMovement> movements = List.of(
                move(ExchangeAccountKind.TREASURY, 1, LocalDate.of(2026, 9, 1), 10, "200", "9600"),
                move(ExchangeAccountKind.TREASURY, 1, LocalDate.of(2026, 9, 2), 11, "-50", "-2475"),
                move(ExchangeAccountKind.TREASURY, 1, LocalDate.of(2026, 9, 3), 11, "-50", "-2380"),
                move(ExchangeAccountKind.CUSTOMER, 1, LocalDate.of(2026, 9, 4), 3, "300", "14550"),
                move(ExchangeAccountKind.SUPPLIER, 1, LocalDate.of(2026, 9, 6), 3, "100", "4900"),
                // After the period: never read into it.
                move(ExchangeAccountKind.CUSTOMER, 1, LocalDate.of(2026, 10, 1), 2, "-300", "-16000"));

        ExchangeDifferenceReport report = ExchangeDifferenceCalculator.report(FROM, TO,
                List.of(supplier, customer, drawer), movements, CURRENCIES, rates("48"), rates("50"));

        assertEquals(List.of(ExchangeAccountKind.TREASURY, ExchangeAccountKind.CUSTOMER,
                ExchangeAccountKind.SUPPLIER), report.rows().stream().map(row -> row.account().kind()).toList());
        ExchangeDifferenceRow treasury = report.rows().get(0);
        // 50 sold at 49.50 and 50 at 47.60 against 48: +75 and -20.
        assertMoney("55", treasury.realizedPeriod());
        assertMoney("55", treasury.lines().stream().map(ExchangeMovementLine::realized)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        assertMoney("200", treasury.unrealizedEnd());
        ExchangeDifferenceRow customer1 = report.rows().get(1);
        assertMoney("300", customer1.ownEnd());
        assertMoney("450", customer1.unrealizedEnd());
        ExchangeDifferenceRow supplier1 = report.rows().get(2);
        assertMoney("-100", supplier1.unrealizedEnd());

        ExchangeDifferenceSummary summary = report.summary();
        assertEquals(3, summary.accounts());
        assertMoney("55", summary.realized());
        assertMoney("550", summary.unrealizedChange());
        assertMoney("605", summary.result());
        assertMoney("605", summary.totalEnd());
        assertEquals(new ExchangeFigures(3, summary.realized(), summary.unrealizedChange(), 0), summary.figures());
    }

    @Test
    @DisplayName("an account with no movement is a row of zeros, and a shop with no account has figures of none")
    void quietAccountsAndNone() {
        ExchangeAccount quiet = new ExchangeAccount(ExchangeAccountKind.TREASURY, 5, "Riyals", 3);
        ExchangeDifferenceRow row = ExchangeDifferenceCalculator.report(FROM, TO, List.of(quiet), List.of(),
                CURRENCIES, Map.of(), Map.of()).rows().get(0);
        assertMoney("0", row.result());
        assertEquals(SAR, row.currency());

        ExchangeDifferenceReport empty = ExchangeDifferenceCalculator.report(FROM, TO, List.of(), List.of(),
                CURRENCIES, Map.of(), Map.of());
        assertFalse(empty.summary().figures().applies());
        assertTrue(new ExchangeFigures(1, BigDecimal.ONE, BigDecimal.TEN, 0).applies());
        assertMoney("11", new ExchangeFigures(1, BigDecimal.ONE, BigDecimal.TEN, 0).result());
    }
}
