package com.hamza.account.features.export;

import com.ibm.icu.text.ArabicShaping;
import com.ibm.icu.text.ArabicShapingException;
import com.ibm.icu.text.Bidi;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * أداة لتشكيل (reshaping) النص العربي قبل كتابته في PDF
 * لضمان ظهور الحروف متصلة وبالاتجاه الصحيح من اليمين لليسار.
 * <p>
 * <b>A number is isolated before the bidi pass, and that is the part easy to break.</b> Under the
 * Unicode bidi rules digits that follow an Arabic word become "Arabic numbers", and a hyphen or a
 * percent sign does not join those - so {@code 2025-10-01} came out of this class as
 * {@code 01-10-2025}, {@code 113.44%} as {@code %113.44}, and a minus with no Arabic near it took
 * the right-to-left paragraph direction and moved to the far end: {@code 11,995.00-}. Every PDF in
 * the application printed its dates backwards inside a sentence and its negatives with the sign
 * trailing. Each number, date, time and percentage is now wrapped in a left-to-right isolate
 * (LRI ... PDI) and the controls are removed from the output, so it reads the way it was written.
 * After an Arabic letter, numbers joined by hyphens are the exception and are isolated one by one
 * ({@link #HYPHEN_BETWEEN_DIGITS}): that is how every screen draws a name, and the paper follows the
 * screen. {@code ArabicTextHelperTest} pins the cases.
 */
public final class ArabicTextHelper {

    private static final ArabicShaping SHAPER =
            new ArabicShaping(ArabicShaping.LETTERS_SHAPE
                    | ArabicShaping.LENGTH_GROW_SHRINK
                    | ArabicShaping.TEXT_DIRECTION_LOGICAL);

    /**
     * A number with the sign and separators it carries - an amount, a date, a time, a percentage.
     * It must end on a digit or a percent sign: a full stop after a number at the end of a
     * sentence belongs to the sentence, and swallowed here it would print on the wrong side.
     */
    // Not touching a Latin letter or another digit on either side: digits inside a product code
    // ("NC7013") are part of a left-to-right word the bidi pass already keeps whole, and isolating
    // them alone printed the code as "7013NC". The digit in each guard stops a backtrack from
    // isolating the front part of a number that runs into letters.
    private static final Pattern NUMBER =
            Pattern.compile("(?<![A-Za-z\\d])[-+]?\\d(?:[\\d,.:/\\-]*\\d)?%?(?![A-Za-z\\d])");

    /**
     * A hyphen between two digits. After an Arabic letter it is not part of either number: the Unicode
     * bidi rules make those digits Arabic numbers, and only a common separator - a comma, a full stop, a
     * colon, a slash - joins Arabic numbers. So every screen draws the pan set "طقم مقلاية 3ق 20-24-28"
     * with 20 on the right and 28 on the left, a list read in the line's direction, while this class
     * isolated "20-24-28" as one left-to-right piece and every PDF printed the sizes the other way round.
     * Each number between the hyphens is isolated on its own now. With no Arabic letter before them the
     * digits are European numbers, which a hyphen does join, and the run stays one piece, as on screen.
     */
    private static final Pattern HYPHEN_BETWEEN_DIGITS = Pattern.compile("(?<=\\d)-(?=\\d)");

    /**
     * A date the application writes into a sentence - a report's period, the date it was printed, the
     * month the delegate reports carry - stays whole after an Arabic word and reads as written. No screen
     * draws those sentences; printed the way the bidi rules would draw them, "من 2025-10-01" came out as
     * {@code 01-10-2025}, the defect the isolation was first written for. Only a real year and month, with
     * or without a day, qualify, so that a name typed with hyphens between its sizes still follows the screen.
     */
    private static final Pattern DATE =
            Pattern.compile("(?:19|20)\\d{2}-(?:0[1-9]|1[0-2])(?:-(?:0[1-9]|[12]\\d|3[01]))?");

    /**
     * Where a left-to-right run begins: a Latin letter. From there the run takes everything up to the
     * next Arabic letter ({@link #endOfLatinRun}), which is what the screen does: after a Latin letter
     * the Unicode bidi rules make the digits that follow left-to-right too, so "Pepsi 330" and
     * "owala-5250" are each one piece. Isolating the number alone made it a neutral of its own that
     * the right-to-left paragraph set apart from its word - every PDF printed "5250-owala" and
     * "330 Pepsi" where the screen showed "owala-5250" and "Pepsi 330".
     * <p>
     * Digits <em>before</em> a Latin letter in an Arabic line stay the Arabic line's, and the paper
     * follows the screen there too, deliberately: "6*1 21 CL" keeps its order, and a code typed
     * "74-AY" prints "AY-74" because that is how every screen in the application draws it. A run
     * beginning at such a digit printed it as typed instead, and so differently from the screen -
     * four codes on a real database of 1,840 items.
     */
    private static final Pattern LATIN_RUN_START = Pattern.compile("(?<![A-Za-z\\d])[A-Za-z]");

    /** Written as escapes: both are invisible, and a literal one is lost to the next edit. */
    static final String LEFT_TO_RIGHT_ISOLATE = "\u2066";
    static final String POP_DIRECTIONAL_ISOLATE = "\u2069";

    private ArabicTextHelper() {
    }

    /**
     * يطبق reshaping و bidi على النص ليظهر بشكل صحيح في PDF.
     *
     * @param text النص الأصلي (يمكن أن يحتوي عربي/أرقام/لاتيني/رموز)
     * @return النص بعد التشكيل بحيث تُكتَب حروفه متصلة وبالترتيب البصري الصحيح
     */
    public static String shape(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        try {
            // 1) إعادة تشكيل الحروف العربية (initial / medial / final / isolated)
            String shaped = SHAPER.shape(isolateNumbers(text));

            // 2) تطبيق Unicode BIDI لإرجاع النص بالترتيب البصري، ثم حذف علامات العزل
            // The paragraph's direction is the original text's first strong letter, as it always was.
            // Asked of the isolated text it would skip what the isolates hold, and a line that is all
            // English - one Latin run - would be set right to left: "Total: 5" as "5 :Total".
            Bidi bidi = new Bidi(shaped.length(), Bidi.DIRECTION_DEFAULT_RIGHT_TO_LEFT);
            bidi.setPara(shaped, paragraphLevel(text), null);
            return bidi.writeReordered(Bidi.DO_MIRRORING | Bidi.REMOVE_BIDI_CONTROLS);
        } catch (ArabicShapingException e) {
            return text;
        }
    }

    /** Left to right when the first strong letter is, otherwise right to left - an empty line included. */
    private static byte paragraphLevel(String text) {
        return Bidi.getBaseDirection(text) == Bidi.LTR ? (byte) 0 : (byte) 1;
    }

    /**
     * Wraps every Latin run and every number outside one in a left-to-right isolate, so the bidi pass
     * moves each as one piece.
     */
    static String isolateNumbers(String text) {
        StringBuilder out = new StringBuilder(text.length() + 8);
        Matcher start = LATIN_RUN_START.matcher(text);
        int from = 0;
        while (from < text.length() && start.find(from)) {
            int end = endOfLatinRun(text, start.start());
            isolateEachNumber(text, from, start.start(), out);
            out.append(LEFT_TO_RIGHT_ISOLATE).append(text, start.start(), end).append(POP_DIRECTIONAL_ISOLATE);
            from = end;
        }
        isolateEachNumber(text, from, text.length(), out);
        return out.toString();
    }

    /**
     * Where a run that begins at {@code start} ends: its last letter, digit or percent sign before the
     * next right-to-left character. The spaces and punctuation after that belong to the Arabic line.
     */
    private static int endOfLatinRun(String text, int start) {
        int end = start + 1;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            byte direction = Character.getDirectionality(c);
            if (direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                    || direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
                    || direction == Character.DIRECTIONALITY_ARABIC_NUMBER) {
                break;
            }
            if (direction == Character.DIRECTIONALITY_LEFT_TO_RIGHT
                    || direction == Character.DIRECTIONALITY_EUROPEAN_NUMBER || c == '%') {
                end = i + 1;
            }
        }
        return end;
    }

    /** The numbers between two Latin runs, each isolated; the bounds are transparent to the guards. */
    private static void isolateEachNumber(String text, int start, int end, StringBuilder out) {
        Matcher number = NUMBER.matcher(text).region(start, end).useTransparentBounds(true);
        int last = start;
        while (number.find()) {
            out.append(text, last, number.start());
            String found = number.group();
            if (arabicLetterBefore(text, number.start()) && !DATE.matcher(found).matches()) {
                isolateBetweenHyphens(found, out);
            } else {
                isolate(found, out);
            }
            last = number.end();
        }
        out.append(text, last, end);
    }

    /** Each number of a hyphenated run isolated alone, the hyphens left to the line between them. */
    private static void isolateBetweenHyphens(String run, StringBuilder out) {
        Matcher hyphen = HYPHEN_BETWEEN_DIGITS.matcher(run);
        int last = 0;
        while (hyphen.find()) {
            isolate(run.substring(last, hyphen.start()), out);
            out.append('-');
            last = hyphen.end();
        }
        isolate(run.substring(last), out);
    }

    private static void isolate(String number, StringBuilder out) {
        out.append(LEFT_TO_RIGHT_ISOLATE).append(number).append(POP_DIRECTIONAL_ISOLATE);
    }

    /**
     * Whether the first letter before {@code index} is Arabic - the condition under which the bidi rules
     * turn the digits that follow into Arabic numbers, which a hyphen does not join.
     */
    private static boolean arabicLetterBefore(String text, int index) {
        for (int i = index - 1; i >= 0; i--) {
            byte direction = Character.getDirectionality(text.charAt(i));
            if (direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) {
                return true;
            }
            if (direction == Character.DIRECTIONALITY_LEFT_TO_RIGHT
                    || direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT) {
                return false;
            }
        }
        return false;
    }
}
