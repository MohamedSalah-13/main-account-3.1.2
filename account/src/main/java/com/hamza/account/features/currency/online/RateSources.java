package com.hamza.account.features.currency.online;

import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Several sources asked in turn, the first answer kept (docs/currency-plan.md §12). A free service can be
 * down, rate-limited or gone on the day somebody presses the button; a second one behind it is what
 * keeps the button working.
 * <p>
 * When none answers, the failure reported is the one the screen can say something useful about: no
 * connection when every source was unreachable, "does not know your currency" when every source that
 * answered said so, and otherwise that the sources answered with something that was not rates.
 */
@Log4j2
public final class RateSources implements RateSource {

    private final List<RateSource> sources;

    public RateSources(List<RateSource> sources) {
        if (Objects.requireNonNull(sources, "sources").isEmpty()) {
            throw new IllegalArgumentException("At least one source");
        }
        this.sources = List.copyOf(sources);
    }

    /** The two this program ships with, over one HTTPS client: ExchangeRate-API, then the currency API. */
    public static RateSources standard(String userAgent) {
        HttpText http = new JdkHttpText(userAgent);
        return new RateSources(List.of(new ExchangeRateApiSource(http), new CurrencyApiSource(http)));
    }

    @Override
    public QuotedRates fetch(String baseCode) throws RateFetchException, InterruptedException {
        List<RateFetchException> failures = new ArrayList<>();
        for (RateSource source : sources) {
            try {
                return source.fetch(baseCode);
            } catch (RateFetchException failure) {
                // The next source may well answer; the log keeps why this one did not.
                log.warn("Exchange-rate source gave no rates: {}", failure.getMessage());
                failures.add(failure);
            }
        }
        throw new RateFetchException(combined(failures), "No source gave rates for " + baseCode, failures.get(0));
    }

    static RateFetchException.Kind combined(List<RateFetchException> failures) {
        List<RateFetchException.Kind> answered = failures.stream()
                .map(RateFetchException::kind)
                .filter(kind -> kind != RateFetchException.Kind.OFFLINE)
                .toList();
        if (answered.isEmpty()) {
            return RateFetchException.Kind.OFFLINE;
        }
        return answered.stream().allMatch(kind -> kind == RateFetchException.Kind.BASE_NOT_OFFERED)
                ? RateFetchException.Kind.BASE_NOT_OFFERED : RateFetchException.Kind.SOURCE;
    }
}
