package com.hamza.account.features.barcodeprint;

/** A language-neutral validation problem; the JavaFX boundary supplies the localized text. */
public record BarcodePrintProblem(Type type, int rowNumber) {

    public enum Type {
        EMPTY_BATCH,
        MISSING_PRINTER,
        MISSING_BARCODE,
        INVALID_COPIES,
        INVALID_PRICE,
        INVALID_LABEL_SIZE,
        INVALID_NAME_SETTINGS,
        /** Code 128 cannot carry the value: it holds a character outside ASCII. */
        UNSUPPORTED_BARCODE,
        /** The bars, at the narrowest width a scanner reads, and their quiet zones exceed the label. */
        BARCODE_TOO_WIDE
    }

    public static BarcodePrintProblem batch(Type type) {
        return new BarcodePrintProblem(type, 0);
    }

    public static BarcodePrintProblem row(Type type, int rowNumber) {
        return new BarcodePrintProblem(type, rowNumber);
    }
}
