package com.hamza.account.features.barcodeprint;

/** A snapshot of the label choices used by one print batch. */
public record BarcodeLabelOptions(
        double widthMm,
        double heightMm,
        boolean doubleLabel,
        boolean showName,
        boolean showPrice,
        boolean showBarcodeNumber,
        BarcodeNameOverflow nameOverflow,
        int nameMaximumCharacters,
        int nameFontSize
) {
}
