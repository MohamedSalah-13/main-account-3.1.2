package com.hamza.account.features.currency.online;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.currency.CurrencyService;
import com.hamza.account.features.currency.RecordedRates;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Objects;

/**
 * The currencies screen's "update from the internet" (docs/currency-plan.md ق-٩, §12): fetch today's
 * rates, show them beside the recorded ones, record the ones somebody ticks.
 * <p>
 * <b>Both halves ask {@code currency.rate.update}.</b> Recording is a write and would ask it anyway;
 * fetching writes nothing, but it is the only thing in this program that goes out to the internet, and
 * a reader who could not record what it found has no reason to send the request.
 * <p>
 * Nothing leaves the machine but the base currency's ISO code, in the address of an HTTPS request.
 */
public final class OnlineRateService {

    private final CurrencyService currencies;
    private final RateSource source;
    private final Clock clock;

    public OnlineRateService(CurrencyService currencies, RateSource source, Clock clock) {
        this.currencies = Objects.requireNonNull(currencies, "currencies");
        this.source = Objects.requireNonNull(source, "source");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Today's rates from the internet beside the recorded ones. Takes seconds and blocks: run it off the
     * JavaFX thread.
     *
     * @throws BusinessRuleException no connection, a base currency no source quotes, or sources that
     *                               answered with something other than rates - each in its own sentence
     */
    public OnlineRatePreview preview() throws DaoException, InterruptedException {
        AuthorizationGuard.require(AppPermissions.CURRENCY_RATE_UPDATE);
        Currency base = currencies.base();
        LocalDate day = LocalDate.now(clock);
        QuotedRates quote;
        try {
            quote = source.fetch(base.code());
        } catch (RateFetchException failure) {
            throw new BusinessRuleException(refusal(failure.kind(), base.code()), failure);
        }
        return OnlineRatePreview.of(quote, currencies.all(), currencies.ratesInForce(day), day);
    }

    /** Records the ticked currencies of {@code preview}, on days that still have no rate - see {@link CurrencyService#recordFetched}. */
    public RecordedRates record(OnlineRatePreview preview, Collection<Integer> chosen) throws DaoException {
        Objects.requireNonNull(preview, "preview");
        return currencies.recordFetched(preview.drafts(Objects.requireNonNull(chosen, "chosen")));
    }

    /** The sentence for a fetch that came back with nothing. */
    static String refusal(RateFetchException.Kind kind, String baseCode) {
        return switch (kind) {
            case OFFLINE -> LanguageManager.getInstance().getString("currency.online.error.offline");
            case BASE_NOT_OFFERED -> LanguageManager.getInstance().getString("currency.online.error.base", baseCode);
            case SOURCE -> LanguageManager.getInstance().getString("currency.online.error.source");
        };
    }
}
