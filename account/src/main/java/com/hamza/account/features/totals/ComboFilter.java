package com.hamza.account.features.totals;

import java.util.List;
import java.util.Locale;

/**
 * Which entries of a picker a typed fragment should leave visible.
 *
 * <p>A shop's customer list is thousands of names long, and a {@code ComboBox} offers no
 * way into it but scrolling. The rule for narrowing it lives here rather than in the
 * control, because a popup, an editor and a selection model all need a running toolkit
 * while the decision itself is a comparison of two strings.</p>
 *
 * <p><b>Arabic is matched as it is typed, not as it was entered.</b> The same name is
 * written أحمد and احمد, حمزه and حمزة, يحيى and يحيي - the difference is a keyboard
 * habit, not a different person. Comparing the raw text means an operator who types the
 * form they know finds nothing and concludes the customer is missing. So both sides are
 * folded to one form first: the hamza carriers to bare alef, ta marbuta to ha, alef
 * maqsura to ya, and the diacritics and tatweel dropped entirely.</p>
 */
public final class ComboFilter {

    private ComboFilter() {
    }

    /**
     * @param entries what the picker holds, in its own order
     * @param typed   what the operator has typed so far
     * @return every entry that contains the fragment, or all of them when nothing is typed
     */
    public static List<String> matching(List<String> entries, String typed) {
        String needle = normalize(typed);
        if (needle.isEmpty()) return List.copyOf(entries);
        return entries.stream()
                .filter(entry -> normalize(entry).contains(needle))
                .toList();
    }

    /** Whether one entry answers to the fragment, on the same rule {@link #matching} uses. */
    public static boolean matches(String entry, String typed) {
        String needle = normalize(typed);
        return needle.isEmpty() || normalize(entry).contains(needle);
    }

    /**
     * The single entry a fragment names, if it names exactly one. Used to accept what the
     * operator typed when they move on without opening the popup - with two candidates
     * there is no answer to accept, and guessing one would file a document against the
     * wrong party.
     */
    public static String soleMatch(List<String> entries, String typed) {
        List<String> found = matching(entries, typed);
        return found.size() == 1 ? found.getFirst() : null;
    }

    /** The comparison form: same letter shapes, no diacritics, no case, no padding. */
    public static String normalize(String value) {
        if (value == null) return "";
        StringBuilder folded = new StringBuilder(value.length());
        for (char character : value.toCharArray()) {
            switch (character) {
                case 'أ', 'إ', 'آ', 'ٱ' -> folded.append('ا'); // أ إ آ ٱ -> ا
                case 'ة' -> folded.append('ه');                               // ة -> ه
                case 'ى' -> folded.append('ي');                               // ى -> ي
                case 'ؤ' -> folded.append('و');                               // ؤ -> و
                case 'ئ' -> folded.append('ي');                               // ئ -> ي
                case 'ـ' -> { }                                                    // ـ tatweel
                default -> {
                    if (!isDiacritic(character)) folded.append(character);
                }
            }
        }
        return folded.toString().trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** The marks written above and below a letter, which carry no identity of their own here. */
    private static boolean isDiacritic(char character) {
        return (character >= 'ً' && character <= 'ْ') || character == 'ٰ';
    }
}
