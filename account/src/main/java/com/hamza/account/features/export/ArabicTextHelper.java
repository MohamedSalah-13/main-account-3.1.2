package com.hamza.account.features.export;

import com.ibm.icu.text.ArabicShaping;
import com.ibm.icu.text.ArabicShapingException;
import com.ibm.icu.text.Bidi;

/**
 * أداة لتشكيل (reshaping) النص العربي قبل كتابته في PDF
 * لضمان ظهور الحروف متصلة وبالاتجاه الصحيح من اليمين لليسار.
 */
public final class ArabicTextHelper {

    private static final ArabicShaping SHAPER =
            new ArabicShaping(ArabicShaping.LETTERS_SHAPE
                    | ArabicShaping.LENGTH_GROW_SHRINK
                    | ArabicShaping.TEXT_DIRECTION_LOGICAL);

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
            String shaped = SHAPER.shape(text);

            // 2) تطبيق Unicode BIDI لإرجاع النص بالترتيب البصري
            Bidi bidi = new Bidi(shaped.length(), Bidi.DIRECTION_DEFAULT_RIGHT_TO_LEFT);
            bidi.setPara(shaped, Bidi.LEVEL_DEFAULT_RTL, null);
            return bidi.writeReordered(Bidi.DO_MIRRORING);
        } catch (ArabicShapingException e) {
            return text;
        }
    }
}
