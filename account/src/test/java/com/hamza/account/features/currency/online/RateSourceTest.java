package com.hamza.account.features.currency.online;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two sources read against the answers their own documentation shows, and the chain that asks them
 * in turn - over a fake {@link HttpText}, since this build cannot reach either (docs/currency-plan.md §12).
 */
class RateSourceTest {

    /** 2026-09-23 00:02:31 UTC. */
    private static final long UPDATED = 1790121751L;

    /** Answers each address from a table; an address it does not know is no connection at all. */
    static final class FakeHttp implements HttpText {
        final Map<String, Response> answers = new HashMap<>();
        final List<String> asked = new ArrayList<>();

        FakeHttp answer(String uri, int status, String body) {
            answers.put(uri, new Response(status, body));
            return this;
        }

        @Override
        public Response get(URI uri) throws RateFetchException {
            asked.add(uri.toString());
            Response response = answers.get(uri.toString());
            if (response == null) {
                throw new RateFetchException(RateFetchException.Kind.OFFLINE, "no route to " + uri);
            }
            return response;
        }
    }

    private static RateFetchException.Kind failure(RateSource source, String base) {
        return assertThrows(RateFetchException.class, () -> source.fetch(base)).kind();
    }

    @Nested
    @DisplayName("ExchangeRate-API")
    class ExchangeRateApi {

        private static final String EGP = "https://open.er-api.com/v6/latest/EGP";
        private final FakeHttp http = new FakeHttp();
        private final ExchangeRateApiSource source = new ExchangeRateApiSource(http);

        private String success(String base) {
            return """
                    {"result":"success","provider":"https://www.exchangerate-api.com",
                     "time_last_update_unix":%d,"time_last_update_utc":"Wed, 23 Sep 2026 00:02:31 +0000",
                     "base_code":"%s",
                     "rates":{"EGP":1,"USD":0.020587,"SAR":0.077205,"KWD":0.006291,"JPY":3.0412,
                              "BTC":"n/a","ZERO":0,"XAU":0}}
                    """.formatted(UPDATED, base);
        }

        @Test
        @DisplayName("reads the quotes with the digits written, and the day they are from")
        void success() throws Exception {
            http.answer(EGP, 200, success("EGP"));
            QuotedRates quote = source.fetch("egp");
            assertEquals(List.of(EGP), http.asked, "the base's code, in capitals, is all that goes out");
            assertEquals("ExchangeRate-API", quote.source());
            assertEquals(LocalDate.of(2026, 9, 23), quote.published());
            assertEquals(new BigDecimal("0.020587"), quote.quoteFor("usd"));
            assertEquals(new BigDecimal("3.0412"), quote.quoteFor("JPY"));
            assertNull(quote.quoteFor("BTC"), "a string is not a quote");
            assertNull(quote.quoteFor("XAU"), "nor is a zero");
        }

        @Test
        @DisplayName("an answer for another base is refused - every rate would be wrong by the difference")
        void anotherBase() {
            http.answer(EGP, 200, success("USD"));
            assertEquals(RateFetchException.Kind.SOURCE, failure(source, "EGP"));
        }

        @Test
        @DisplayName("a base it does not know is said as such; any other error, an error page or a limit is a source failure")
        void refusals() {
            http.answer(EGP, 404, "{\"result\":\"error\",\"error-type\":\"unsupported-code\"}");
            assertEquals(RateFetchException.Kind.BASE_NOT_OFFERED, failure(source, "EGP"));
            http.answer(EGP, 429, "<html>Too Many Requests</html>");
            assertEquals(RateFetchException.Kind.SOURCE, failure(source, "EGP"));
            http.answer(EGP, 200, "{\"result\":\"error\",\"error-type\":\"malformed-request\"}");
            assertEquals(RateFetchException.Kind.SOURCE, failure(source, "EGP"));
            http.answer(EGP, 200, "{\"result\":\"success\",\"base_code\":\"EGP\",\"rates\":{}}");
            assertEquals(RateFetchException.Kind.SOURCE, failure(source, "EGP"), "no rates, no date");
        }

        @Test
        @DisplayName("no answer at all is no connection")
        void offline() {
            assertEquals(RateFetchException.Kind.OFFLINE, failure(source, "EGP"));
        }

        @Test
        @DisplayName("nothing but a three-letter code is ever put into the address")
        void onlyACode() {
            assertThrows(IllegalArgumentException.class, () -> source.fetch("EGP/../x"));
            assertThrows(IllegalArgumentException.class, () -> source.fetch("ج.م"));
            assertTrue(http.asked.isEmpty());
        }
    }

    @Nested
    @DisplayName("the currency API")
    class CurrencyApi {

        private static final String CDN = "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/sar.json";
        private static final String PAGES = "https://latest.currency-api.pages.dev/v1/currencies/sar.json";
        private static final String ANSWER = """
                {"date":"2026-09-22","sar":{"egp":12.9525,"usd":0.2666,"1inch":0.5,"sar":1}}
                """;
        private final FakeHttp http = new FakeHttp();
        private final CurrencyApiSource source = new CurrencyApiSource(http);

        @Test
        @DisplayName("reads the table under the base's own code, in small letters, and its date")
        void success() throws Exception {
            http.answer(CDN, 200, ANSWER);
            QuotedRates quote = source.fetch("SAR");
            assertEquals(LocalDate.of(2026, 9, 22), quote.published());
            assertEquals(new BigDecimal("12.9525"), quote.quoteFor("EGP"));
            assertNull(quote.quoteFor("1INCH"), "a crypto-currency is not a currency here");
            assertEquals(List.of(CDN), http.asked);
        }

        @Test
        @DisplayName("the second mirror is asked when the first is down, as its documentation says")
        void mirror() throws Exception {
            http.answer(PAGES, 200, ANSWER);
            assertEquals(new BigDecimal("0.2666"), source.fetch("SAR").quoteFor("USD"));
            assertEquals(List.of(CDN, PAGES), http.asked);
        }

        @Test
        @DisplayName("a file that does not exist is a base it does not know")
        void unknownBase() {
            http.answer(CDN, 404, "Not found").answer(PAGES, 404, "Not found");
            assertEquals(RateFetchException.Kind.BASE_NOT_OFFERED, failure(source, "SAR"));
        }

        @Test
        @DisplayName("a body with no date, or no table for the base, is a source failure")
        void broken() {
            http.answer(CDN, 200, "{\"sar\":{\"usd\":0.2666}}");
            assertEquals(RateFetchException.Kind.SOURCE, failure(source, "SAR"));
            http.answer(CDN, 200, "{\"date\":\"2026-09-22\",\"egp\":{\"usd\":0.02}}");
            assertEquals(RateFetchException.Kind.SOURCE, failure(source, "SAR"));
        }
    }

    @Nested
    @DisplayName("the sources asked in turn")
    class InTurn {

        private static RateSource answering(QuotedRates quote) {
            return base -> quote;
        }

        private static RateSource failing(RateFetchException.Kind kind) {
            return base -> {
                throw new RateFetchException(kind, kind.name());
            };
        }

        private final QuotedRates quote = new QuotedRates("S", "s", "EGP", LocalDate.of(2026, 9, 23),
                Map.of("USD", new BigDecimal("0.02")));

        @Test
        @DisplayName("the first answer is kept, and a failed source does not stop the next")
        void firstAnswer() throws Exception {
            assertSame(quote, new RateSources(List.of(answering(quote), failing(RateFetchException.Kind.SOURCE)))
                    .fetch("EGP"));
            assertSame(quote, new RateSources(List.of(failing(RateFetchException.Kind.SOURCE), answering(quote)))
                    .fetch("EGP"));
        }

        @Test
        @DisplayName("none answering: offline only when none was reached, the base when all that answered said so")
        void combined() {
            assertEquals(RateFetchException.Kind.OFFLINE, failure(new RateSources(List.of(
                    failing(RateFetchException.Kind.OFFLINE), failing(RateFetchException.Kind.OFFLINE))), "EGP"));
            assertEquals(RateFetchException.Kind.BASE_NOT_OFFERED, failure(new RateSources(List.of(
                    failing(RateFetchException.Kind.BASE_NOT_OFFERED), failing(RateFetchException.Kind.OFFLINE))), "EGP"));
            assertEquals(RateFetchException.Kind.SOURCE, failure(new RateSources(List.of(
                    failing(RateFetchException.Kind.BASE_NOT_OFFERED), failing(RateFetchException.Kind.SOURCE))), "EGP"));
        }
    }
}
