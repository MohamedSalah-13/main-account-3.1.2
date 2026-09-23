package com.hamza.account.features.currency.online;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;

/**
 * ExchangeRate-API's open access endpoint, {@code https://open.er-api.com/v6/latest/<BASE>} - the first
 * source asked (docs/currency-plan.md §12).
 * <p>
 * Chosen because it is free, needs no key, and quotes the currencies an Arabic-market shop deals in -
 * the pound, the riyal, the dirham, the dinars - against any of them as the base; the European Central
 * Bank's list, behind most other free services, has none of them. It updates once a day, and its terms
 * ask for attribution, which the screen gives by naming it beside the rates.
 * <p>
 * The answer, as documented: {@code {"result":"success", "base_code":"EGP",
 * "time_last_update_unix":1790121600, "rates":{"EGP":1, "USD":0.0206, ...}}}, or
 * {@code {"result":"error", "error-type":"unsupported-code"}} for a base it does not know.
 */
public final class ExchangeRateApiSource implements RateSource {

    static final String NAME = "ExchangeRate-API";
    static final String SITE = "exchangerate-api.com";
    static final String ENDPOINT = "https://open.er-api.com/v6/latest/";

    private final HttpText http;

    public ExchangeRateApiSource(HttpText http) {
        this.http = Objects.requireNonNull(http, "http");
    }

    @Override
    public QuotedRates fetch(String baseCode) throws RateFetchException, InterruptedException {
        String code = QuoteJson.requireCode(baseCode);
        HttpText.Response response = http.get(URI.create(ENDPOINT + code));
        JsonNode root = QuoteJson.parse(response.body());
        if (root == null) {
            throw new RateFetchException(RateFetchException.Kind.SOURCE,
                    NAME + " answered " + response.status() + " with no JSON");
        }
        String result = root.path("result").asText("");
        if ("error".equals(result)) {
            String type = root.path("error-type").asText("");
            throw new RateFetchException("unsupported-code".equals(type)
                    ? RateFetchException.Kind.BASE_NOT_OFFERED : RateFetchException.Kind.SOURCE,
                    NAME + " refused " + code + ": " + type);
        }
        if (response.status() != 200 || !"success".equals(result)) {
            throw new RateFetchException(RateFetchException.Kind.SOURCE,
                    NAME + " answered " + response.status() + " with result '" + result + "'");
        }
        // An answer for another base would be read as this one's, every rate wrong by the difference.
        if (!code.equals(root.path("base_code").asText(""))) {
            throw new RateFetchException(RateFetchException.Kind.SOURCE,
                    NAME + " answered for " + root.path("base_code").asText("?") + " when asked for " + code);
        }
        long updated = root.path("time_last_update_unix").asLong(0);
        Map<String, BigDecimal> quotes = QuoteJson.quotes(root.path("rates"));
        if (updated <= 0 || quotes.isEmpty()) {
            throw new RateFetchException(RateFetchException.Kind.SOURCE, NAME + " answered with no rates or no date");
        }
        LocalDate published = Instant.ofEpochSecond(updated).atZone(ZoneOffset.UTC).toLocalDate();
        return new QuotedRates(NAME, SITE, code, published, quotes);
    }
}
