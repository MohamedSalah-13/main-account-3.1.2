package com.hamza.account.features.items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Filter combinations the operator has named and kept.
 * <p>
 * A shop asks the same four or five questions of its catalogue every week - what is below
 * its minimum in the drinks group, what has no barcode, what has never sold - and the
 * filter panel makes each of those a dozen clicks. Naming one turns it into a choice from
 * a list.
 * <p>
 * They live in Java {@code Preferences} beside the rest of this application's per-user
 * settings, not in the database: a saved filter is one person's habit on one machine, it
 * is worthless to anyone else, and a table for it would be a schema change and a migration
 * for something nobody would ever report on.
 * <p>
 * <b>The typed search text is deliberately not saved.</b> A saved filter answers a
 * standing question - "what is below its minimum" - and the text is what the operator is
 * looking for inside that answer right now; storing it would make every recall also
 * re-type a search from days ago.
 */
public final class SavedItemFilters {

    /** Bumped only if the encoding below ever has to change shape. */
    private static final String FORMAT = "1";
    private static final String FORMAT_KEY = "__format";
    private static final String SEPARATOR = ";";
    private static final String ASSIGN = "=";

    private final Preferences node;

    public SavedItemFilters(Preferences node) {
        this.node = node;
    }

    /**
     * The names in the order they were created, each with the filter it stands for.
     * <p>
     * A stored entry that cannot be decoded is skipped rather than thrown: preferences
     * outlive the code that wrote them, and one unreadable row must not cost the operator
     * the other four.
     */
    public Map<String, ItemCatalogFilter> all() {
        Map<String, ItemCatalogFilter> saved = new LinkedHashMap<>();
        for (String name : names()) {
            String encoded = node.get(name, null);
            if (encoded == null) continue;
            ItemCatalogFilter filter = decode(encoded);
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

    /** Saves under this name, replacing any filter already kept under it. */
    public void save(String name, ItemCatalogFilter filter) {
        if (name == null || name.isBlank() || filter == null) return;
        node.put(FORMAT_KEY, FORMAT);
        node.put(name.trim(), encode(filter));
    }

    public void delete(String name) {
        if (name != null) node.remove(name);
    }

    public ItemCatalogFilter get(String name) {
        String encoded = name == null ? null : node.get(name, null);
        return encoded == null ? null : decode(encoded);
    }

    /**
     * The filter as one string of {@code key=value} pairs.
     * <p>
     * Written out field by field rather than by serialising the record, so that adding a
     * field to {@link ItemCatalogFilter} cannot silently invalidate everything a user has
     * already saved: an unknown key is ignored on the way back in, and a missing one takes
     * the empty filter's value.
     */
    static String encode(ItemCatalogFilter filter) {
        StringBuilder text = new StringBuilder();
        append(text, "scope", filter.searchScope().name());
        append(text, "match", filter.matchMode().name());
        append(text, "main", filter.mainGroupId());
        append(text, "sub", filter.subGroupId());
        append(text, "active", filter.active().name());
        append(text, "barcode", filter.hasBarcode().name());
        append(text, "expiry", filter.tracksExpiry().name());
        append(text, "balance", filter.balance().name());
        append(text, "usage", filter.usage().name());
        append(text, "minPrice", filter.minSellPrice());
        append(text, "maxPrice", filter.maxSellPrice());
        return text.toString();
    }

    static ItemCatalogFilter decode(String encoded) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : encoded.split(SEPARATOR)) {
            int split = pair.indexOf(ASSIGN);
            if (split > 0) values.put(pair.substring(0, split), pair.substring(split + 1));
        }
        try {
            return new ItemCatalogFilter("",
                    enumValue(values.get("scope"), ItemCatalogFilter.SearchScope.class, ItemCatalogFilter.SearchScope.ANY),
                    enumValue(values.get("match"), ItemCatalogFilter.MatchMode.class, ItemCatalogFilter.MatchMode.AUTO),
                    integer(values.get("main")),
                    integer(values.get("sub")),
                    enumValue(values.get("active"), ItemCatalogFilter.Tristate.class, ItemCatalogFilter.Tristate.ANY),
                    enumValue(values.get("barcode"), ItemCatalogFilter.Tristate.class, ItemCatalogFilter.Tristate.ANY),
                    enumValue(values.get("expiry"), ItemCatalogFilter.Tristate.class, ItemCatalogFilter.Tristate.ANY),
                    enumValue(values.get("balance"), ItemCatalogFilter.BalanceRule.class, ItemCatalogFilter.BalanceRule.ANY),
                    decimal(values.get("minPrice")),
                    decimal(values.get("maxPrice")),
                    enumValue(values.get("usage"), ItemCatalogFilter.UsageRule.class, ItemCatalogFilter.UsageRule.ANY));
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    private static void append(StringBuilder text, String key, Object value) {
        if (value == null) return;
        text.append(key).append(ASSIGN).append(value).append(SEPARATOR);
    }

    /** An enum constant that no longer exists reads back as the neutral value, never as a crash. */
    private static <E extends Enum<E>> E enumValue(String stored, Class<E> type, E fallback) {
        if (stored == null) return fallback;
        try {
            return Enum.valueOf(type, stored);
        } catch (IllegalArgumentException gone) {
            return fallback;
        }
    }

    private static Integer integer(String stored) {
        try {
            return stored == null ? null : Integer.valueOf(stored);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static Double decimal(String stored) {
        try {
            return stored == null ? null : Double.valueOf(stored);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
