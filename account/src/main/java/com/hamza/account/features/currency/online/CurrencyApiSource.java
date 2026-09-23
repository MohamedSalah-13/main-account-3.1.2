package com.hamza.account.features.currency.online;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The free currency API published as {@code @fawazahmed0/currency-api} - the second source, asked only
 * when the first gave nothing (docs/currency-plan.md §12).
 * <p>
 * It is a daily file per base currency served from two mirrors, which its own documentation says to try
 * in turn: jsDelivr's CDN first, Cloudflare Pages second. No key, no limit, and the same Arabic-market
 * currencies. The answer: {@code {"date":"2026-09-23", "egp":{"usd":0.0206, "sar":0.0773, ...}}} - the
 * codes in small letters and the table under the base's own code. A base it does not know is a 404.
 */
public final class CurrencyApiSource implements RateSource {

    static final String NAME = "Currency API";
    static final String SITE = "github.com/fawazahmed0/exchange-api";
    static final List<String> MIRRORS = List.of(
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/",
            "https://latest.currency-api.pages.dev/v1/currencies/");

    private final HttpText http;

    public CurrencyApiSource(HttpText http) {
        this.http = Objects.requireNonNull(http, "http");
    }

    @Override
    public QuotedRates fetch(String baseCode) throws RateFetchException, InterruptedException {
        String code = QuoteJson.requireCode(baseCode);
        String file = code.toLowerCase(Locale.ROOT) + ".json";
        List<RateFetchException> failures = new ArrayList<>();
        for (String mirror : MIRRORS) {
            try {
                return read(code, http.get(URI.create(mirror + file)));
            } catch (RateFetchException e) {
                failures.add(e);
            }
        }
        // Said the way the chain of sources says it: a mirror that answered with rubbish is not made
        // "no connection" by the mirror behind it being unreachable.
        throw new RateFetchException(RateSources.combined(failures), NAME + " gave no rates for " + code,
                failures.get(0));
    }

    private static QuotedRates read(String code, HttpText.Response response) throws RateFetchException {
        if (response.status() == 404) {
            throw new RateFetchException(RateFetchException.Kind.BASE_NOT_OFFERED, NAME + " has no file for " + code);
        }
        JsonNode root = response.status() == 200 ? QuoteJson.parse(response.body()) : null;
        if (root == null) {
            throw new RateFetchException(RateFetchException.Kind.SOURCE,
                    NAME + " answered " + response.status() + " with no JSON");
        }
        Map<String, BigDecimal> quotes = QuoteJson.quotes(root.path(code.toLowerCase(Locale.ROOT)));
        if (quotes.isEmpty()) {
            throw new RateFetchException(RateFetchException.Kind.SOURCE, NAME + " answered with no rates for " + code);
        }
        LocalDate published;
        try {
            published = LocalDate.parse(root.path("date").asText(""));
        } catch (DateTimeParseException e) {
            throw new RateFetchException(RateFetchException.Kind.SOURCE, NAME + " answered with no date", e);
        }
        return new QuotedRates(NAME, SITE, code, published, quotes);
    }
}
