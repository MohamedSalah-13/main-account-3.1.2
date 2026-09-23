package com.hamza.account.features.currency;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.hamza.account.features.currency.CurrencyFixtures.DAY;
import static com.hamza.account.features.currency.CurrencyFixtures.EGP;
import static com.hamza.account.features.currency.CurrencyFixtures.KWD;
import static com.hamza.account.features.currency.CurrencyFixtures.OLD_LIRA;
import static com.hamza.account.features.currency.CurrencyFixtures.SAR;
import static com.hamza.account.features.currency.CurrencyFixtures.USD;
import static com.hamza.account.features.currency.CurrencyFixtures.rate;
import static com.hamza.account.features.currency.CurrencyFixtures.signInWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the currency service decides. Deleting a currency goes through {@code DeletionService}, which
 * needs its registry and a database, so a delete here is only ever refused before it gets there.
 */
class CurrencyServiceTest {

    private final CurrencyFixtures.Repository repository =
            new CurrencyFixtures.Repository(EGP, SAR, USD, KWD, OLD_LIRA);
    private final CurrencyService service = new CurrencyService(repository, CurrencyTransactions.direct());

    private static BigDecimal of(String value) {
        return new BigDecimal(value);
    }

    @Nested
    @DisplayName("reading")
    class Reading {

        @Test
        @DisplayName("a picker is offered the active currencies; the screen sees the stopped as well")
        void pickers() throws Exception {
            assertEquals(List.of(EGP, SAR, USD, KWD), service.active());
            assertEquals(5, service.all().size());
        }

        @Test
        @DisplayName("the base is the one row flagged, and its absence is a refusal rather than a null")
        void base() throws Exception {
            assertEquals(EGP, service.base());
            CurrencyService empty = new CurrencyService(new CurrencyFixtures.Repository(USD),
                    CurrencyTransactions.direct());
            assertEquals("currency.error.base.missing",
                    assertThrows(BusinessRuleException.class, empty::base).getMessage());
        }

        @Test
        @DisplayName("the rate history is the screen's, so it asks the screen's key")
        void historyIsGuarded() throws Exception {
            repository.rates.add(rate(1, USD, DAY, "48.5"));
            signInWith(AppPermissions.CURRENCY_RATE_UPDATE);
            assertThrows(BusinessRuleException.class, () -> service.rates(USD.id()));
            signInWith(AppPermissions.CURRENCY_SHOW);
            assertEquals(1, service.rates(USD.id()).size());
        }

        @Test
        @DisplayName("a conversion uses the latest rate dated on the day or before, and one for the base")
        void rateInForce() throws Exception {
            repository.rates.add(rate(1, USD, DAY.minusDays(5), "48.0"));
            repository.rates.add(rate(2, USD, DAY, "48.5"));
            repository.rates.add(rate(3, USD, DAY.plusDays(1), "49.0"));
            assertEquals(of("48.5"), service.requireRate(USD, DAY));
            assertEquals(of("48.0"), service.requireRate(USD, DAY.minusDays(1)));
            assertEquals(BigDecimal.ONE, service.requireRate(EGP, DAY));
            assertEquals(of("4850.00"), service.toBase(of("100"), USD, DAY));
        }

        @Test
        @DisplayName("no rate on or before the day is a refusal - never a zero and never a one")
        void noRate() {
            repository.rates.add(rate(1, USD, DAY.plusDays(1), "49.0"));
            assertEquals("currency.error.no.rate", assertThrows(UserValidationException.class,
                    () -> service.requireRate(USD, DAY)).getMessage());
        }

        @Test
        @DisplayName("the converter lists every other active currency, the stopped ones left out")
        void converter() throws Exception {
            repository.rates.add(rate(1, USD, DAY, "48.5"));
            List<CurrencyConversion> lines = service.convert(of("100"), USD.id(), DAY);
            assertEquals(List.of(EGP, SAR, KWD), lines.stream().map(CurrencyConversion::target).toList());
            assertEquals(of("4850.00"), lines.get(0).amount());
        }
    }

    @Nested
    @DisplayName("writing a currency")
    class WritingACurrency {

        @Test
        @DisplayName("asks currency.update, and a refusal writes nothing")
        void guarded() {
            signInWith(AppPermissions.CURRENCY_SHOW, AppPermissions.CURRENCY_RATE_UPDATE);
            assertThrows(BusinessRuleException.class,
                    () -> service.save(new CurrencyDraft(0, "EUR", "يورو", "€", 2, true, 0)));
            assertThrows(BusinessRuleException.class, () -> service.setBase(SAR.id()));
            assertThrows(BusinessRuleException.class, () -> service.delete(USD.id()));
            assertTrue(repository.inserted.isEmpty());
            assertTrue(repository.calls.isEmpty());
        }

        @Test
        @DisplayName("a new currency answers its generated id; an edit answers its own")
        void saveAnswersTheId() throws Exception {
            signInWith(AppPermissions.CURRENCY_UPDATE);
            assertEquals(40, service.save(new CurrencyDraft(0, "eur", "يورو", "€", 2, true, 0)));
            assertEquals("EUR", repository.inserted.get(0).code());
            assertEquals(USD.id(), service.save(new CurrencyDraft(USD.id(), "USD", USD.name(), "US$", 2, true, 3)));
        }

        @Test
        @DisplayName("a unique key refusing a second save of one code is said as the same sentence")
        void duplicateTranslated() {
            signInWith(AppPermissions.CURRENCY_UPDATE);
            repository.duplicateKey = "currency_code_uk";
            assertEquals("currency.error.code.taken", assertThrows(UserValidationException.class,
                    () -> service.save(new CurrencyDraft(0, "EUR", "يورو", "€", 2, true, 0))).getMessage());
            repository.duplicateKey = "currency_name_uk";
            assertEquals("currency.error.name.taken", assertThrows(UserValidationException.class,
                    () -> service.save(new CurrencyDraft(0, "EUR", "يورو", "€", 2, true, 0))).getMessage());
        }

        @Test
        @DisplayName("the base is refused a delete before the registry is asked")
        void baseNotDeleted() {
            signInWith(AppPermissions.CURRENCY_UPDATE);
            assertEquals("currency.error.base.delete", assertThrows(UserValidationException.class,
                    () -> service.delete(EGP.id())).getMessage());
            assertTrue(repository.deleted.isEmpty());
        }
    }

    @Nested
    @DisplayName("moving the base")
    class MovingTheBase {

        @Test
        @DisplayName("locks every currency, then takes the flag off the old base before putting it on the new")
        void order() throws Exception {
            signInWith(AppPermissions.CURRENCY_UPDATE);
            service.setBase(SAR.id());
            assertEquals(List.of("lockAll", "clearBase", "markBase:" + SAR.id()), repository.calls);
            assertEquals(SAR.id(), service.base().id());
        }

        @Test
        @DisplayName("is refused once any rate is recorded, and nothing moves")
        void lockedByARate() {
            signInWith(AppPermissions.CURRENCY_UPDATE);
            repository.rates.add(rate(1, USD, DAY, "48.5"));
            assertEquals("currency.error.base.locked", assertThrows(UserValidationException.class,
                    () -> service.setBase(SAR.id())).getMessage());
            assertEquals(List.of("lockAll"), repository.calls);
        }

        @Test
        @DisplayName("is refused for a stopped currency")
        void notToAStoppedOne() {
            signInWith(AppPermissions.CURRENCY_UPDATE);
            assertEquals("currency.error.base.inactive", assertThrows(UserValidationException.class,
                    () -> service.setBase(OLD_LIRA.id())).getMessage());
        }

        @Test
        @DisplayName("to the currency that already is the base does nothing at all")
        void alreadyTheBase() throws Exception {
            signInWith(AppPermissions.CURRENCY_UPDATE);
            service.setBase(EGP.id());
            assertEquals(List.of("lockAll"), repository.calls);
        }
    }

    @Nested
    @DisplayName("writing a rate")
    class WritingARate {

        @Test
        @DisplayName("asks currency.rate.update - currency.update alone does not record a rate")
        void guarded() {
            signInWith(AppPermissions.CURRENCY_UPDATE, AppPermissions.CURRENCY_SHOW);
            assertThrows(BusinessRuleException.class,
                    () -> service.saveRate(new ExchangeRateDraft(0, USD.id(), DAY, of("48.5"), null)));
            assertThrows(BusinessRuleException.class, () -> service.deleteRate(1));
            assertTrue(repository.insertedRates.isEmpty());
        }

        @Test
        @DisplayName("reads the currency under its lock, then writes the rate")
        void recorded() throws Exception {
            signInWith(AppPermissions.CURRENCY_RATE_UPDATE);
            assertEquals(90, service.saveRate(new ExchangeRateDraft(0, USD.id(), DAY, of("48.5"), "البنك")));
            assertEquals(List.of("lockForRate:" + USD.id()), repository.calls);
            assertEquals("البنك", repository.insertedRates.get(0).notes());
        }

        @Test
        @DisplayName("a second rate for the same currency on the same day is refused; the first is corrected instead")
        void oneADay() throws Exception {
            signInWith(AppPermissions.CURRENCY_RATE_UPDATE);
            repository.rates.add(rate(5, USD, DAY, "48.5"));
            assertEquals("currency.rate.error.day.taken", assertThrows(UserValidationException.class,
                    () -> service.saveRate(new ExchangeRateDraft(0, USD.id(), DAY, of("48.6"), null))).getMessage());
            assertEquals(5, service.saveRate(new ExchangeRateDraft(5, USD.id(), DAY, of("48.6"), null)));
            assertEquals(of("48.6"), repository.updatedRates.get(0).rate());
        }

        @Test
        @DisplayName("an edit may not move a rate to another currency")
        void notMovedBetweenCurrencies() {
            signInWith(AppPermissions.CURRENCY_RATE_UPDATE);
            repository.rates.add(rate(5, USD, DAY, "48.5"));
            assertEquals("currency.rate.error.currency", assertThrows(UserValidationException.class,
                    () -> service.saveRate(new ExchangeRateDraft(5, SAR.id(), DAY, of("12.9"), null))).getMessage());
        }

        @Test
        @DisplayName("the base is refused a rate, and a rate that is not there is refused a delete")
        void refusals() {
            signInWith(AppPermissions.CURRENCY_RATE_UPDATE);
            assertEquals("currency.rate.error.base", assertThrows(UserValidationException.class,
                    () -> service.saveRate(new ExchangeRateDraft(0, EGP.id(), DAY, BigDecimal.ONE, null))).getMessage());
            assertEquals("currency.rate.error.not.found", assertThrows(UserValidationException.class,
                    () -> service.deleteRate(404)).getMessage());
        }
    }
}
