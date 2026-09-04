package com.hamza.account.features.scalebarcode;

/**
 * How a scale writes a barcode: a fixed prefix identifying the scale, the item's code,
 * the embedded value, and optionally a check digit.
 *
 * <pre>
 *   27 | 00001 | 00050 | 1
 *   ^^   ^^^^^   ^^^^^   ^
 *   |    |       |       check digit
 *   |    |       value (weight or total price)
 *   |    item code
 *   scale prefix
 * </pre>
 *
 * <p>The parts must add up to the barcode's length, so exactly one of them has to be the
 * remainder - and it is the value, because every other part is a number the operator
 * reads off the scale's own manual. That is why {@link #deriveValueDigits} takes the
 * total length and works the value out, rather than asking for five numbers that can
 * contradict each other.
 *
 * <p>This is also where a long-standing mislabelling is settled. The setting behind
 * {@link #prefixDigits()} was shown on the settings screen as "عدد أرقام الوزن" - the
 * number of value digits - while the parser used it as the length of the scale prefix. A
 * user who set it to what the label asked for pushed the prefix from {@code 27} to
 * {@code 27000}, after which no scale barcode was ever recognised again, with nothing on
 * screen to say why.
 *
 * <p>{@link #hasCheckDigit()} is deliberately separate from whether the check digit is
 * <em>verified</em>: the first says the digit occupies a position in the barcode, the
 * second says its value is checked. The parser used to assume the first unconditionally
 * while offering a setting for the second, so a scale that emits no check digit lost the
 * last digit of every weight - 1.234 kg read as 0.123.
 *
 * @param prefix       the number a barcode from this scale starts with
 * @param prefixDigits how many digits that prefix occupies, leading zeros included
 * @param itemDigits   how many digits carry the item's code
 * @param valueDigits  how many digits carry the weight or the total price
 */
public record ScaleBarcodeFormat(int prefix, int prefixDigits, int itemDigits, int valueDigits,
                                 boolean hasCheckDigit) {

    /** Message keys naming why a layout cannot be used. */
    public static final String PREFIX_DIGITS_REQUIRED = "barcode.error.format.prefix.digits";
    public static final String ITEM_DIGITS_REQUIRED = "barcode.error.format.item.digits";
    public static final String VALUE_DIGITS_REQUIRED = "barcode.error.format.value.digits";
    public static final String PREFIX_TOO_LONG = "barcode.error.format.prefix.too.long";

    /** The length a barcode from this scale must have. */
    public int totalLength() {
        return prefixDigits + itemDigits + valueDigits + (hasCheckDigit ? 1 : 0);
    }

    /** The prefix as it appears at the front of a barcode, zero-padded to its width. */
    public String prefixText() {
        return prefixDigits <= 0 ? "" : String.format("%0" + prefixDigits + "d", prefix);
    }

    /**
     * The key of the message naming why this layout cannot read anything, or {@code null}
     * when it is sound.
     * <p>
     * Returned rather than thrown: the settings screen asks this of a layout the operator
     * is still typing, where a refusal is something to show beside the field, not an
     * error to report.
     */
    public String problemKey() {
        if (prefixDigits < 1) return PREFIX_DIGITS_REQUIRED;
        if (itemDigits < 1) return ITEM_DIGITS_REQUIRED;
        if (valueDigits < 1) return VALUE_DIGITS_REQUIRED;
        if (prefix < 0 || String.valueOf(prefix).length() > prefixDigits) return PREFIX_TOO_LONG;
        return null;
    }

    public boolean isUsable() {
        return problemKey() == null;
    }

    /**
     * The layout of a scale that emits barcodes of {@code totalLength} digits, with the
     * value taking whatever the prefix, the item code and the check digit leave.
     * <p>
     * A negative remainder is kept as it is rather than clamped to zero: it is what
     * {@link #problemKey()} reports, and hiding it would turn "these numbers do not add
     * up" into "the value is empty", which says nothing about what to change.
     */
    public static ScaleBarcodeFormat deriveValueDigits(int prefix, int prefixDigits, int itemDigits,
                                                       int totalLength, boolean hasCheckDigit) {
        int valueDigits = totalLength - prefixDigits - itemDigits - (hasCheckDigit ? 1 : 0);
        return new ScaleBarcodeFormat(prefix, prefixDigits, itemDigits, valueDigits, hasCheckDigit);
    }
}
