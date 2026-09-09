package com.hamza.account.features.totals;

import com.hamza.account.document.TotalsSearchCriteria;
import com.hamza.account.type.InvoiceType;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Named totals-search presets kept for one operator on one computer.
 *
 * <p>The free-text query is deliberately not persisted. It is the temporary lookup
 * inside a standing filter, so recalling a preset must not restore an old word from a
 * previous session.</p>
 */
public final class SavedTotalsFilters {

    private static final String FORMAT = "1";
    private static final String FORMAT_KEY = "__format";
    private static final String SEPARATOR = ";";
    private static final String ASSIGN = "=";

    private final Preferences node;

    public SavedTotalsFilters(Preferences node) {
        this.node = node;
    }

    public Map<String, TotalsSearchCriteria> all() {
        Map<String, TotalsSearchCriteria> saved = new LinkedHashMap<>();
        for (String name : names()) {
            TotalsSearchCriteria filter = get(name);
            if (filter != null) saved.put(name, filter);
        }
        return saved;
    }

    public List<String> names() {
        try {
            List<String> keys = new ArrayList<>(List.of(node.keys()));
            keys.remove(FORMAT_KEY);
            keys.sort(String::compareToIgnoreCase);
            return keys;
        } catch (BackingStoreException unreadable) {
            return List.of();
        }
    }

    /** Saves under this name, replacing an existing preset with the same name. */
    public void save(String name, TotalsSearchCriteria filter) {
        if (name == null || name.isBlank() || filter == null) return;
        node.put(FORMAT_KEY, FORMAT);
        node.put(name.trim(), encode(filter));
    }

    public void delete(String name) {
        if (name != null) node.remove(name);
    }

    public TotalsSearchCriteria get(String name) {
        String encoded = name == null ? null : node.get(name, null);
        return encoded == null ? null : decode(encoded);
    }

    static String encode(TotalsSearchCriteria filter) {
        StringBuilder text = new StringBuilder();
        append(text, "from", filter.dateFrom());
        append(text, "to", filter.dateTo());
        append(text, "invoice", filter.invoiceNumber());
        appendText(text, "party", filter.partyName());
        appendText(text, "delegate", filter.delegateName());
        append(text, "type", filter.invoiceType() == null ? null : filter.invoiceType().name());
        appendText(text, "user", filter.enteredByUsername());
        append(text, "min", filter.minTotal());
        append(text, "max", filter.maxTotal());
        return text.toString();
    }

    static TotalsSearchCriteria decode(String encoded) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : encoded.split(SEPARATOR)) {
            int split = pair.indexOf(ASSIGN);
            if (split > 0) values.put(pair.substring(0, split), pair.substring(split + 1));
        }
        try {
            // Both dates are optional, so a preset may legitimately carry neither - that is
            // what "every invoice this customer ever had" is saved as. Parsing them
            // unconditionally threw, and the catch below turned the whole preset into null.
            return new TotalsSearchCriteria(
                    date(values.get("from")),
                    date(values.get("to")),
                    integer(values.get("invoice")),
                    decodedText(values.get("party")),
                    decodedText(values.get("delegate")),
                    enumValue(values.get("type"), InvoiceType.class),
                    decodedText(values.get("user")),
                    decimal(values.get("min")),
                    decimal(values.get("max")),
                    null);
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    private static void append(StringBuilder text, String key, Object value) {
        if (value != null) text.append(key).append(ASSIGN).append(value).append(SEPARATOR);
    }

    /** Text is encoded because party and employee names may legitimately contain '=' or ';'. */
    private static void appendText(StringBuilder text, String key, String value) {
        if (value == null) return;
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
        append(text, key, encoded);
    }

    private static String decodedText(String stored) {
        if (stored == null) return null;
        return new String(Base64.getUrlDecoder().decode(stored), StandardCharsets.UTF_8);
    }

    private static LocalDate date(String stored) {
        return stored == null ? null : LocalDate.parse(stored);
    }

    private static Integer integer(String stored) {
        return stored == null ? null : Integer.valueOf(stored);
    }

    private static BigDecimal decimal(String stored) {
        return stored == null ? null : new BigDecimal(stored);
    }

    private static <E extends Enum<E>> E enumValue(String stored, Class<E> type) {
        if (stored == null) return null;
        try {
            return Enum.valueOf(type, stored);
        } catch (IllegalArgumentException gone) {
            return null;
        }
    }
}
