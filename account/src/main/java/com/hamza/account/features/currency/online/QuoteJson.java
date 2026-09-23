package com.hamza.account.features.currency.online;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * What the sources have in common: reading an answer as JSON, and reading a table of quotes out of it.
 * <p>
 * Numbers are read as {@link BigDecimal} from their text, so a quote reaches {@link OnlineRateMath} with
 * the digits the source wrote rather than whatever a {@code double} kept of them.
 */
final class QuoteJson {

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    /** An ISO 4217 code. The sources also list crypto-currencies and metals; those are not currencies here. */
    private static final Pattern ISO_CODE = Pattern.compile("[A-Za-z]{3}");

    private QuoteJson() {
    }

    /** The body as JSON, or {@code null} when it is not JSON at all - an error page, a portal's login form. */
    static JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return JSON.readTree(body);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Every three-letter code in {@code table} with a positive number beside it, the code in capitals.
     * Anything else - a name longer than three letters, a string, a zero, a negative - is left out rather
     * than guessed at.
     */
    static Map<String, BigDecimal> quotes(JsonNode table) {
        Map<String, BigDecimal> quotes = new HashMap<>();
        if (table == null || !table.isObject()) {
            return quotes;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = table.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode value = field.getValue();
            if (!ISO_CODE.matcher(field.getKey()).matches() || !value.isNumber()) {
                continue;
            }
            BigDecimal quote = value.decimalValue();
            if (quote.signum() > 0) {
                quotes.put(field.getKey().toUpperCase(Locale.ROOT), quote);
            }
        }
        return quotes;
    }

    /** The base's code as a source is asked for it, or a refusal: nothing else goes into an address. */
    static String requireCode(String baseCode) {
        if (baseCode == null || !ISO_CODE.matcher(baseCode).matches()) {
            throw new IllegalArgumentException("Not an ISO 4217 code: " + baseCode);
        }
        return baseCode.toUpperCase(Locale.ROOT);
    }
}
