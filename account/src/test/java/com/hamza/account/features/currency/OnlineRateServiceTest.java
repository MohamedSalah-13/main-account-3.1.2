package com.hamza.account.features.currency;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.features.currency.online.OnlineRateLine;
import com.hamza.account.features.currency.online.OnlineRatePreview;
import com.hamza.account.features.currency.online.OnlineRateService;
import com.hamza.account.features.currency.online.QuotedRates;
import com.hamza.account.features.currency.online.RateFetchException;
import com.hamza.account.features.currency.online.RateSource;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hamza.account.features.currency.CurrencyFixtures.DAY;
import static com.hamza.account.features.currency.CurrencyFixtures.EGP;
import static com.hamza.account.features.currency.CurrencyFixtures.KWD;
import static com.hamza.account.features.currency.CurrencyFixtures.OLD_LIRA;
import static com.hamza.account.features.currency.CurrencyFixtures.SAR;
import static com.hamza.account.features.currency.CurrencyFixtures.USD;
import static com.hamza.account.features.currency.CurrencyFixtures.rate;
import static com.hamza.account.features.currency.CurrencyFixtures.signInWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Update from the internet" end to end, over the in-memory repository and a source answering from a
 * table: what is fetched, who may, what a failure says, and that a recorded day is never replaced
 * (docs/currency-plan.md ق-٩, §12).
 */
class OnlineRateServiceTest {

    private final CurrencyFixtures.Repository repository =
            new CurrencyFixtures.Repository(EGP, SAR, USD, KWD, OLD_LIRA);
    private final CurrencyService currencies = new CurrencyService(repository, CurrencyTransactions.direct());
    /** The bases the source was asked about, in order. */
    private final List<String> asked = new ArrayList<>();
    private RateFetchException.Kind failWith;

    private final RateSource source = base -> {
        asked.add(base);
        if (failWith != null) {
            throw new RateFetchException(failWith, "test");
        }
        return new QuotedRates("ExchangeRate-API", "exchangerate-api.com", base, DAY, Map.of(
                "USD", new BigDecimal("0.020587"), "SAR", new BigDecimal("0.077205"),
                "KWD", new BigDecimal("0.006291")));
    };
    private final OnlineRateService service = new OnlineRateService(currencies, source,
            Clock.fixed(DAY.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));

    @BeforeEach
    void operator() {
        signInWith(AppPermissions.CURRENCY_RATE_UPDATE);
        repository.rates.add(rate(1, USD, DAY.minusDays(1), "48.50"));
        repository.rates.add(rate(2, KWD, DAY, "158.00"));
    }

    @Test
    @DisplayName("the source is asked about the base alone, and the preview is dated the machine's today")
    void preview() throws Exception {
        OnlineRatePreview preview = service.preview();
        assertEquals(List.of("EGP"), asked);
        assertEquals(DAY, preview.day());
        assertEquals(List.of("SAR", "USD", "KWD"), preview.lines().stream().map(line -> line.currency().code()).toList(),
                "the stopped lira is not offered");
        assertEquals(OnlineRateLine.Status.RECORDED_TODAY, preview.lines().get(2).status(), "the dinar typed today");
        assertEquals(Set.of(SAR.id(), USD.id()), preview.preselected());
    }

    @Test
    @DisplayName("without currency.rate.update nothing is fetched and nothing recorded")
    void guarded() {
        signInWith(AppPermissions.CURRENCY_SHOW);
        assertThrows(BusinessRuleException.class, service::preview);
        assertTrue(asked.isEmpty(), "no request leaves the machine for a reader who could not record what it found");
        OnlineRatePreview preview = OnlineRatePreview.of(new QuotedRates("S", "s", "EGP", DAY,
                Map.of("USD", new BigDecimal("0.02"))), List.of(USD), Map.of(), DAY);
        assertThrows(BusinessRuleException.class, () -> service.record(preview, List.of(USD.id())));
        assertTrue(repository.insertedRates.isEmpty());
    }

    @Test
    @DisplayName("each way of getting nothing has its own sentence, and the cause is kept for the log")
    void failures() {
        Set<String> sentences = new HashSet<>();
        for (RateFetchException.Kind kind : RateFetchException.Kind.values()) {
            failWith = kind;
            BusinessRuleException refusal = assertThrows(BusinessRuleException.class, service::preview);
            assertInstanceOf(RateFetchException.class, refusal.getCause());
            assertFalse(refusal.getMessage().startsWith("currency.online."), "a sentence, not a key: " + kind);
            sentences.add(refusal.getMessage());
        }
        assertEquals(3, sentences.size());
        failWith = RateFetchException.Kind.BASE_NOT_OFFERED;
        assertTrue(assertThrows(BusinessRuleException.class, service::preview).getMessage().contains("EGP"),
                "names the currency the source does not know");
        assertNotEquals("currency.online.error.offline",
                LanguageManager.getInstance().getString("currency.online.error.offline"));
    }

    @Test
    @DisplayName("records the ticked lines, dated today and noted with the source")
    void record() throws Exception {
        OnlineRatePreview preview = service.preview();
        RecordedRates result = service.record(preview, preview.preselected());
        assertEquals(List.of(SAR.id(), USD.id()), result.recorded());
        assertTrue(result.kept().isEmpty());
        assertEquals(List.of("lockForRate:" + SAR.id(), "lockForRate:" + USD.id()), repository.calls);
        ExchangeRateDraft usd = repository.insertedRates.get(1);
        assertEquals(DAY, usd.effectiveDate());
        assertEquals(new BigDecimal("48.5743"), usd.rate());
        assertEquals("ExchangeRate-API 2026-09-23", usd.notes());
    }

    @Test
    @DisplayName("a day recorded meanwhile - at another till, or at the same instant - is kept, and the rest go in")
    void keepsARecordedDay() throws Exception {
        OnlineRatePreview preview = service.preview();
        // Somebody typed the riyal's rate while the dialog was open.
        repository.rates.add(rate(3, SAR, DAY, "12.90"));
        RecordedRates result = service.record(preview, preview.preselected());
        assertEquals(List.of(USD.id()), result.recorded());
        assertEquals(List.of(SAR.id()), result.kept());
        assertEquals(1, repository.insertedRates.size());

        repository.duplicateKey = "currency_rate_day_uk";
        RecordedRates race = service.record(preview, List.of(USD.id()));
        assertTrue(race.recorded().isEmpty(), "the unique key refused it");
        assertEquals(List.of(USD.id()), race.kept());
    }

    @Test
    @DisplayName("a batch is held to the rules a typed rate is, and may only add rows")
    void rules() {
        assertThrows(IllegalArgumentException.class, () -> currencies.recordFetched(List.of(
                new ExchangeRateDraft(5, USD.id(), DAY, new BigDecimal("48.5"), null))));
        assertEquals("currency.rate.error.base", assertThrows(UserValidationException.class,
                () -> currencies.recordFetched(List.of(new ExchangeRateDraft(0, EGP.id(), DAY, BigDecimal.ONE, null))))
                .getMessage());
        assertTrue(repository.insertedRates.isEmpty());
    }
}
