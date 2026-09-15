package com.hamza.account.features.invoice;

import java.text.Normalizer;
import java.util.Locale;

/** Language-tolerant matching for the saved-invoice item search. */
public final class InvoiceDetailsSearch {

    private InvoiceDetailsSearch() {
    }

    /** Matches every query word against the item code, name or unit, in any order. */
    public static boolean matches(InvoiceDetailsLine line, String query) {
        if (line == null) {
            return false;
        }
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank()) {
            return true;
        }
        String searchable = normalize(line.itemId() + " " + line.itemName() + " " + line.unitName());
        for (String word : normalizedQuery.split("\\s+")) {
            if (!searchable.contains(word)) {
                return false;
            }
        }
        return true;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFKD);
        StringBuilder normalized = new StringBuilder(decomposed.length());
        boolean previousWasSpace = true;
        for (int offset = 0; offset < decomposed.length(); ) {
            int codePoint = decomposed.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.getType(codePoint) == Character.NON_SPACING_MARK) {
                continue;
            }
            if (Character.isDigit(codePoint)) {
                normalized.append(Character.getNumericValue(codePoint));
                previousWasSpace = false;
                continue;
            }
            if (Character.isWhitespace(codePoint)) {
                if (!previousWasSpace) {
                    normalized.append(' ');
                    previousWasSpace = true;
                }
                continue;
            }
            normalized.appendCodePoint(codePoint);
            previousWasSpace = false;
        }
        return normalized.toString().strip().toLowerCase(Locale.ROOT);
    }
}
