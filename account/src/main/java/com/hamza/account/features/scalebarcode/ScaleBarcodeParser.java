package com.hamza.account.features.scalebarcode;

import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;

/**
 * Splits a scale barcode into the item's code and the number embedded in it.
 * <p>
 * Every refusal is a {@link UserValidationException} carrying the sentence the operator
 * needs. That is not decoration: {@code ErrorReporter} shows the message of a validation
 * failure and hides the message of everything else behind a reference code, so a refusal
 * thrown as anything else reaches the cashier as "an unexpected error occurred".
 * <p>
 * It refuses on the layout before it touches the barcode. The old code read the parts
 * with {@code substring} and trusted the numbers to add up, so a mis-set item width threw
 * {@code StringIndexOutOfBoundsException} - or {@code Double.parseDouble("")} - from
 * inside the parse, which is the technical path again, and says nothing about the setting
 * that caused it.
 */
public final class ScaleBarcodeParser {

    private ScaleBarcodeParser() {
    }

    /**
     * @param validateCheckDigit whether the check digit's value is verified. Whether the
     *                           barcode <em>carries</em> one is
     *                           {@link ScaleBarcodeFormat#hasCheckDigit()}, and the two
     *                           are different questions.
     */
    public static ScaleBarcodeParts parse(String barcode, ScaleBarcodeFormat format, boolean validateCheckDigit)
            throws UserValidationException {
        String problem = format.problemKey();
        if (problem != null) {
            throw refusal(problem, format.totalLength());
        }
        if (barcode == null || !barcode.matches("\\d+")) {
            throw refusal("barcode.error.not.numeric");
        }
        if (barcode.length() != format.totalLength()) {
            throw refusal("barcode.error.wrong.length", format.totalLength(), barcode.length());
        }

        String scaleCode = barcode.substring(0, format.prefixDigits());
        if (!scaleCode.equals(format.prefixText())) {
            throw refusal("barcode.error.wrong.scale.code", format.prefixText(), scaleCode);
        }

        int itemEnd = format.prefixDigits() + format.itemDigits();
        String itemCode = barcode.substring(format.prefixDigits(), itemEnd);
        String valuePart = barcode.substring(itemEnd, itemEnd + format.valueDigits());

        if (itemCode.matches("0+")) {
            throw refusal("barcode.error.zero.item.code");
        }
        double rawValue = Double.parseDouble(valuePart);
        if (rawValue <= 0) {
            throw refusal("barcode.error.zero.weight");
        }

        if (validateCheckDigit && format.hasCheckDigit()) {
            char printed = barcode.charAt(barcode.length() - 1);
            char expected = ScaleBarcodeCheckDigit.of(barcode.substring(0, barcode.length() - 1));
            if (printed != expected) {
                throw refusal("barcode.error.wrong.check.digit", expected, printed);
            }
        }

        return new ScaleBarcodeParts(itemCode, rawValue);
    }

    private static UserValidationException refusal(String key, Object... arguments) {
        return new UserValidationException(LanguageManager.getInstance().getString(key, arguments));
    }
}
