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

    /** More than a page number can ever need, and short of overflowing a parse. */
    private static final int MAX_DIGITS = 9;

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
     * Whether a page field may hold this text <em>while it is being typed</em>.
     *
     * <p>Deliberately here, beside the parser, and not in the general text filters: what a
     * page box accepts and what {@link #targetPage} understands have to be the same
     * alphabet. A filter that let through something the parser refuses gives a field you
     * can type into that quietly does nothing; one that blocked something the parser
     * accepts would refuse the Arabic-Indic digits an Arabic keyboard actually produces.
     * One definition, used by both.</p>
     *
     * <p>Empty is allowed: a field has to be clearable on the way to a different number.</p>
     */
    public static boolean isTypablePageText(String candidate) {
        if (candidate == null || candidate.isEmpty()) return true;
        if (candidate.length() > MAX_DIGITS) return false;
        for (char character : candidate.toCharArray()) {
            if (digitValue(character) < 0) return false;
        }
        return true;
    }

    /**
     * Arabic-Indic digits are what an Arabic keyboard produces, and they are the same
     * numbers - typing ٥ into a page box has to mean page five.
     */
    private static String normalize(String typed) {
        StringBuilder digits = new StringBuilder(typed.length());
        for (char character : typed.trim().toCharArray()) {
            if (Character.isWhitespace(character)) continue;
            int value = digitValue(character);
            if (value < 0) return "";                                      // not a number
            digits.append((char) ('0' + value));
        }
        return digits.toString();
    }

    /** The value of a digit in any of the three scripts a keyboard here produces, else -1. */
    private static int digitValue(char character) {
        if (character >= '0' && character <= '9') return character - '0';
        if (character >= '٠' && character <= '٩') return character - '٠';   // ٠-٩
        if (character >= '۰' && character <= '۹') return character - '۰';   // ۰-۹ extended
        return -1;
    }
}
