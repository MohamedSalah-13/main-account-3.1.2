package com.hamza.account.features.treasury;

import com.hamza.account.features.currency.Currency;
import com.hamza.account.treasury.TreasuryBalanceSummary;
import com.hamza.account.treasury.TreasuryType;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A treasury in a foreign currency (V81, docs/currency-plan.md §11), through the rules the treasury
 * services apply - with no database: the currencies and their rates come from {@link Fake}, and each
 * treasury is a {@link TreasuryBalanceSummary} as the view would answer it.
 */
class ForeignTreasuryTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 23);
    private static final Currency USD = new Currency(3, "USD", "دولار أمريكي", "$", "$", 2, false, true, 3);
    private static final Currency SAR = new Currency(2, "SAR", "ريال سعودي", "ر.س", "SAR", 2, false, true, 2);
    private static final Currency STOPPED = new Currency(6, "TRY", "ليرة تركية", "₺", "", 2, false, false, 6);

    /** The currencies and their rates on {@link #DAY}. */
    static final class Fake implements TreasuryCurrencies {
        final Map<Integer, Currency> currencies = new HashMap<>(Map.of(3, USD, 2, SAR, 6, STOPPED));
        final Map<Integer, BigDecimal> rates = new HashMap<>(Map.of(3, new BigDecimal("48.5"),
                2, new BigDecimal("12.93")));

        @Override
        public Currency find(Integer currencyId) {
            return currencyId == null ? null : currencies.get(currencyId);
        }

        @Override
        public BigDecimal rateOn(int currencyId, LocalDate day) {
            return rates.get(currencyId);
        }
    }

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    /** A treasury holding {@code own} in {@code currencyId} ({@code null} is the base), worth {@code book}. */
    private static TreasuryBalanceSummary treasury(int id, Integer currencyId, String own, String book) {
        return new TreasuryBalanceSummary(id, "t" + id, TreasuryType.CASH, true, 0, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, d(book), currencyId, BigDecimal.ZERO, d(own));
    }

    private static TreasuryTransferCommand transfer(String sent, String received, String fee) {
        return new TreasuryTransferCommand(1, 2, d(sent), DAY, "", 2, fee == null ? null : d(fee),
                received == null ? null : d(received));
    }

    @Nested
    @DisplayName("a transfer between treasuries")
    class Transfers {

        private final Fake currencies = new Fake();
        private final TreasuryTransferService service = new TreasuryTransferService(null, null, currencies);

        @Test
        @DisplayName("buying dollars stores the pounds paid and the dollars received")
        void buying() throws Exception {
            var figures = service.figures(treasury(1, null, "9000", "9000"), treasury(2, 3, "0", "0"),
                    transfer("4850", "100", null));
            assertEquals(d("4850"), figures.baseAmount());
            assertNull(figures.amountFrom());
            assertEquals(d("100"), figures.amountTo());
        }

        @Test
        @DisplayName("riyals for dollars are valued at the riyal's recorded rate - and without one, refused")
        void foreignToForeign() throws Exception {
            var figures = service.figures(treasury(1, 2, "500", "6465"), treasury(2, 3, "0", "0"),
                    transfer("375", "100", null));
            assertEquals(d("4848.75"), figures.baseAmount());
            currencies.rates.remove(2);
            assertEquals("currency.error.no.rate", assertThrows(UserValidationException.class,
                    () -> service.figures(treasury(1, 2, "500", "6465"), treasury(2, 3, "0", "0"),
                            transfer("375", "100", null))).getMessage());
        }

        @Test
        @DisplayName("selling dollars needs no rate at all: both amounts are what was counted")
        void sellingNeedsNoRate() throws Exception {
            currencies.rates.clear();
            var figures = service.figures(treasury(1, 3, "100", "4800"), treasury(2, null, "0", "0"),
                    transfer("100", "5000", null));
            assertEquals(d("5000"), figures.baseAmount());
            assertEquals(d("100"), figures.amountFrom());
        }

        @Test
        @DisplayName("a fee out of a treasury in a foreign currency is refused - it would be an expense in it")
        void feeFromAForeignTreasury() {
            assertThrows(BusinessRuleException.class, () -> service.figures(treasury(1, 3, "100", "4800"),
                    treasury(2, null, "0", "0"), transfer("50", "2500", "1")));
            // From a treasury in the base, into a foreign one, the fee is the base's own expense.
            assertDoesNotThrow(() -> service.figures(treasury(1, null, "9000", "9000"),
                    treasury(2, 3, "0", "0"), transfer("4850", "100", "10")));
        }

        @Test
        @DisplayName("a dollar drawer is checked in dollars, not against its book value")
        void theOwnBalanceIsWhatIsChecked() {
            TreasuryBalanceSummary drawer = treasury(1, 3, "100", "4800");
            assertDoesNotThrow(() -> TreasuryTransferService.requireEnough(drawer, d("100")));
            assertThrows(BusinessRuleException.class, () -> TreasuryTransferService.requireEnough(drawer, d("150")),
                    "150 dollars are not in a drawer holding 100, whatever 4,800 pounds would say");
        }
    }

    @Nested
    @DisplayName("a deposit or a withdrawal")
    class Cash {

        private final Fake currencies = new Fake();
        private final TreasuryCashService service = new TreasuryCashService(null, null, currencies);

        private CashMovementCommand deposit(String amount) {
            return new CashMovementCommand(1, CashDirection.DEPOSIT, CashCategory.NORMAL, d(amount), DAY,
                    "deposit", "", 2);
        }

        @Test
        @DisplayName("into a treasury in the base, it is what it always was")
        void base() throws Exception {
            var value = service.valueOf(treasury(1, null, "0", "0"), deposit("250.50"));
            assertEquals(d("250.50"), value.baseAmount());
            assertNull(value.foreignAmount());
            assertNull(value.rate());
        }

        @Test
        @DisplayName("into a dollar drawer, it is valued at the day's rate, copied")
        void foreign() throws Exception {
            var value = service.valueOf(treasury(1, 3, "0", "0"), deposit("50"));
            assertEquals(d("2425.00"), value.baseAmount());
            assertEquals(d("50"), value.foreignAmount());
            assertEquals(d("48.5"), value.rate());
        }

        @Test
        @DisplayName("with no rate for the day it is refused, and so is a cent the currency does not have")
        void refusals() {
            assertEquals("treasury.exchange.error.places", assertThrows(UserValidationException.class,
                    () -> service.valueOf(treasury(1, 3, "0", "0"), deposit("50.555"))).getMessage());
            currencies.rates.clear();
            assertEquals("currency.error.no.rate", assertThrows(UserValidationException.class,
                    () -> service.valueOf(treasury(1, 3, "0", "0"), deposit("50"))).getMessage());
        }
    }

    @Nested
    @DisplayName("a treasury's opening balance")
    class Opening {

        @Test
        @DisplayName("in the base: what was typed, nothing foreign")
        void base() {
            TreasuryOpening opening = TreasuryOpening.base(d("1000"));
            assertNull(opening.currencyId());
            assertEquals(d("1000"), opening.amount());
            assertNull(opening.foreign());
            assertNull(opening.rate());
        }

        @Test
        @DisplayName("in dollars: valued at the opening day's rate; opened empty, no rate is needed")
        void foreign() throws Exception {
            TreasuryOpening opening = TreasuryOpening.foreign(USD, d("200"), d("48.5"));
            assertEquals(3, opening.currencyId());
            assertEquals(d("9700.00"), opening.amount());
            assertEquals(d("200"), opening.foreign());
            assertEquals(d("48.5"), opening.rate());

            TreasuryOpening empty = TreasuryOpening.foreign(USD, null, null);
            assertEquals(0, empty.amount().signum());
            assertEquals(0, empty.foreign().signum());
            assertNull(empty.rate(), "the CHECK takes no rate for a zero opening");
        }

        @Test
        @DisplayName("a foreign opening without a rate, or in a stopped currency, is refused")
        void refusals() {
            assertEquals("currency.error.no.rate", assertThrows(UserValidationException.class,
                    () -> TreasuryOpening.foreign(USD, d("200"), null)).getMessage());
            assertEquals("treasury.currency.error.inactive", assertThrows(UserValidationException.class,
                    () -> TreasuryOpening.foreign(STOPPED, d("0"), null)).getMessage());
            assertEquals("currency.error.not.found", assertThrows(UserValidationException.class,
                    () -> TreasuryOpening.foreign(null, d("0"), null)).getMessage());
        }
    }

    @Test
    @DisplayName("the guard refuses a treasury in a foreign currency and lets one in the base through")
    void guard() {
        TreasuryCurrencyGuard guard = new TreasuryCurrencyGuard(id -> id == 7 ? "درج الدولار" : null);
        assertDoesNotThrow(() -> guard.requireBaseCurrency(1));
        assertThrows(BusinessRuleException.class, () -> guard.requireBaseCurrency(7));
        assertThrows(BusinessRuleException.class, () -> guard.requireBaseCurrency(7, "user.shift.error.treasury.foreign"));
    }
}
