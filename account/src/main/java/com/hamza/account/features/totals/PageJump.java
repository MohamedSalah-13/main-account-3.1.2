package com.hamza.account.features.totals;

import java.util.OptionalInt;

/**
 * What a typed page number means.
 *
 * <p>Twenty-four pages is twenty-three clicks to reach the last one and twenty-three back,
 * so the pager takes a number. The rule for reading it is here rather than in the control
 * because it is arithmetic on two integers, and because every interesting case is a
 * mistake: a blank field, a word, a zero, a negative, or a page beyond the end.</p>
 *
 * <p>Out of range is <b>clamped, not refused</b>. Someone who types 500 into a list of 24
 * pages means the last one, and answering an obvious intention with an error message is
 * worse than answering it - while a page that does not exist would show an empty table and
 * look like a search that found nothing.</p>
 */
public final class PageJump {

    private PageJump() {
    }

    /**
     * @param typed     what the operator wrote, in the numbering they see - page one is 1
     * @param pageCount how many pages the current result has, at least one
     * @return the zero-based page to load, or empty when nothing was typed or it is not a
     *         number at all
     */
    public static OptionalInt targetPage(String typed, int pageCount) {
        if (typed == null) return OptionalInt.empty();
        String digits = normalize(typed);
        if (digits.isEmpty()) return OptionalInt.empty();
        long requested;
        try {
            requested = Long.parseLong(digits);
        } catch (NumberFormatException notANumber) {
            return OptionalInt.empty();
        }
        long highest = Math.max(1, pageCount);
        long clamped = Math.min(Math.max(requested, 1), highest);
        return OptionalInt.of((int) clamped - 1);
    }

    /**
     * Arabic-Indic digits are what an Arabic keyboard produces, and they are the same
     * numbers - typing ٥ into a page box has to mean page five.
     */
    private static String normalize(String typed) {
        StringBuilder digits = new StringBuilder(typed.length());
        for (char character : typed.trim().toCharArray()) {
            if (character >= '٠' && character <= '٩') {          // ٠-٩
                digits.append((char) ('0' + character - '٠'));
            } else if (character >= '۰' && character <= '۹') {   // ۰-۹ (extended)
                digits.append((char) ('0' + character - '۰'));
            } else if (Character.isDigit(character)) {
                digits.append(character);
            } else if (!Character.isWhitespace(character)) {
                return "";                                                 // not a number
            }
        }
        return digits.toString();
    }
}
