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
 * {@code ArabicTextHelperTest} pins the cases.
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
    private static final Pattern NUMBER = Pattern.compile("[-+]?\\d(?:[\\d,.:/\\-]*\\d)?%?");

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
            Bidi bidi = new Bidi(shaped.length(), Bidi.DIRECTION_DEFAULT_RIGHT_TO_LEFT);
            bidi.setPara(shaped, Bidi.LEVEL_DEFAULT_RTL, null);
            return bidi.writeReordered(Bidi.DO_MIRRORING | Bidi.REMOVE_BIDI_CONTROLS);
        } catch (ArabicShapingException e) {
            return text;
        }
    }

    /** Wraps every number in a left-to-right isolate, so the bidi pass moves it as one piece. */
    static String isolateNumbers(String text) {
        return NUMBER.matcher(text).replaceAll(match -> Matcher.quoteReplacement(
                LEFT_TO_RIGHT_ISOLATE + match.group() + POP_DIRECTIONAL_ISOLATE));
    }
}
