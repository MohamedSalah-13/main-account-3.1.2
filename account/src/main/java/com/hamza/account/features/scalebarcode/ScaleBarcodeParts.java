package com.hamza.account.features.scalebarcode;

/**
 * What a scale barcode carries: which item, and the number printed into it.
 *
 * @param itemCode the item's code exactly as it appears, leading zeros kept - it is
 *                 matched against {@code items.barcode} as text, not as a number
 * @param rawValue the embedded digits as a number, still unscaled: what it counts -
 *                 grams or piastres - is {@link ScaleBarcodeValueType}'s answer
 */
public record ScaleBarcodeParts(String itemCode, double rawValue) {
}
