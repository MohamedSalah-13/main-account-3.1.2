package com.hamza.account.features.currency.online;

import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.currency.ExchangeRateDraft;
import com.hamza.account.features.currency.RateInForce;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which currency is offered, which is ticked, and what recording them would write (ق-٩, §12). */
class OnlineRatePreviewTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 23);

    private static final Currency EGP = new Currency(1, "EGP", "جنيه مصري", "ج.م", "L.E.", 2, true, true, 1);
    private static final Currency USD = new Currency(3, "USD", "دولار أمريكي", "$", "", 2, false, true, 2);
    private static final Currency SAR = new Currency(2, "SAR", "ريال سعودي", "ر.س", "SAR", 2, false, true, 3);
    private static final Currency KWD = new Currency(4, "KWD", "دينار كويتي", "د.ك", "", 3, false, true, 4);
    private static final Currency AED = new Currency(5, "AED", "درهم إماراتي", "د.إ", "", 2, false, true, 5);
    private static final Currency TRY = new Currency(6, "TRY", "ليرة تركية", "₺", "", 2, false, false, 6);
    private static final Currency XYZ = new Currency(7, "XYZ", "عملة المحل", "x", "", 2, false, true, 7);

    /** One pound buys: the dollar at 48.5743, the riyal at 12.9525, the dinar at 158.957, the dirham at 13.2261. */
    private static QuotedRates quote(LocalDate published) {
        return new QuotedRates("ExchangeRate-API", "exchangerate-api.com", "EGP", published, Map.of(
                "USD", new BigDecimal("0.020587"), "SAR", new BigDecimal("0.077205"),
                "KWD", new BigDecimal("0.006291"), "AED", new BigDecimal("0.075608"),
                "TRY", new BigDecimal("0.7")));
    }

    private static RateInForce inForce(Currency currency, LocalDate from, String rate) {
        return new RateInForce(currency.id(), from, new BigDecimal(rate), null);
    }

    private static OnlineRatePreview preview(Map<Integer, RateInForce> inForce, LocalDate published) {
        return OnlineRatePreview.of(quote(published), List.of(EGP, USD, SAR, KWD, AED, TRY, XYZ), inForce, DAY);
    }

    /** The dollar moved 0.15%, the riyal has no rate yet, the dinar was typed today, the dirham jumped. */
    private static final Map<Integer, RateInForce> IN_FORCE = Map.of(
            USD.id(), inForce(USD, DAY.minusDays(1), "48.50"),
            KWD.id(), inForce(KWD, DAY, "158.00"),
            AED.id(), inForce(AED, DAY.minusDays(7), "11.90"));

    private static OnlineRateLine line(OnlineRatePreview preview, Currency currency) {
        return preview.lines().stream().filter(line -> line.currency().id() == currency.id()).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("every active currency but the base, in the order given")
    void which() {
        assertEquals(List.of("USD", "SAR", "KWD", "AED", "XYZ"),
                preview(IN_FORCE, DAY).lines().stream().map(line -> line.currency().code()).toList(),
                "the base is worth one of itself; a stopped currency is not offered");
    }

    @Test
    @DisplayName("each line's status")
    void statuses() {
        OnlineRatePreview preview = preview(IN_FORCE, DAY);
        OnlineRateLine usd = line(preview, USD);
        assertEquals(OnlineRateLine.Status.READY, usd.status());
        assertEquals(new BigDecimal("48.5743"), usd.fetched());
        assertEquals(new BigDecimal("0.15"), usd.changePercent());

        OnlineRateLine sar = line(preview, SAR);
        assertEquals(OnlineRateLine.Status.READY, sar.status(), "a first rate has nothing to move from");
        assertNull(sar.changePercent());

        OnlineRateLine kwd = line(preview, KWD);
        assertEquals(OnlineRateLine.Status.RECORDED_TODAY, kwd.status(), "a rate typed today stays");
        assertEquals(new BigDecimal("158.957"), kwd.fetched(), "shown beside it for comparison");
        assertFalse(kwd.recordable());

        OnlineRateLine aed = line(preview, AED);
        assertEquals(OnlineRateLine.Status.LARGE_MOVE, aed.status(), "11.90 to 13.2261 is 11.14%");
        assertTrue(aed.recordable());

        OnlineRateLine xyz = line(preview, XYZ);
        assertEquals(OnlineRateLine.Status.NOT_OFFERED, xyz.status());
        assertNull(xyz.fetched());
    }

    @Test
    @DisplayName("a move of exactly ten percent is not large; one beyond it is, either way")
    void boundary() {
        // 1 / 0.02 = 50; against 45.4545... the move is 10.00%, against 55.56 it is -10.01%.
        QuotedRates quote = new QuotedRates("S", "s", "EGP", DAY, Map.of("USD", new BigDecimal("0.02")));
        assertEquals(OnlineRateLine.Status.READY, OnlineRatePreview.of(quote, List.of(USD),
                Map.of(USD.id(), inForce(USD, DAY.minusDays(1), "45.4545454545")), DAY).lines().get(0).status());
        assertEquals(OnlineRateLine.Status.LARGE_MOVE, OnlineRatePreview.of(quote, List.of(USD),
                Map.of(USD.id(), inForce(USD, DAY.minusDays(1), "55.56")), DAY).lines().get(0).status());
    }

    @Test
    @DisplayName("ticked for you: a small move or a first rate - never a large move, a recorded day, or stale figures")
    void preselected() {
        assertEquals(Set.of(USD.id(), SAR.id()), preview(IN_FORCE, DAY).preselected());
        assertEquals(Set.of(USD.id(), SAR.id()), preview(IN_FORCE, DAY.minusDays(3)).preselected(),
                "three days old is still offered as usual");
        OnlineRatePreview stale = preview(IN_FORCE, DAY.minusDays(4));
        assertTrue(stale.stale());
        assertTrue(stale.preselected().isEmpty(), "four days old is shown, offered, and ticked by nobody");
        assertEquals(0, preview(IN_FORCE, DAY.plusDays(1)).publishedDaysAgo(), "a source a day ahead is not negative");
    }

    @Test
    @DisplayName("what is recorded: the ticked lines that may be, dated the day of the fetch, noted with the source")
    void drafts() {
        OnlineRatePreview preview = preview(IN_FORCE, DAY.minusDays(1));
        List<ExchangeRateDraft> drafts = preview.drafts(List.of(USD.id(), KWD.id(), XYZ.id(), AED.id(), 99));
        assertEquals(List.of(USD.id(), AED.id()), drafts.stream().map(ExchangeRateDraft::currencyId).toList(),
                "a tick on a recorded day or a currency not quoted is ignored, not obeyed");
        ExchangeRateDraft usd = drafts.get(0);
        assertTrue(usd.isNew());
        assertEquals(DAY, usd.effectiveDate());
        assertEquals(new BigDecimal("48.5743"), usd.rate());
        assertEquals("ExchangeRate-API 2026-09-22", usd.notes());
        assertTrue(preview.drafts(List.of()).isEmpty());
    }
}
