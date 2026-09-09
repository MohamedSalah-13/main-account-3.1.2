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
        INVALID_NAME_SETTINGS
    }

    public static BarcodePrintProblem batch(Type type) {
        return new BarcodePrintProblem(type, 0);
    }

    public static BarcodePrintProblem row(Type type, int rowNumber) {
        return new BarcodePrintProblem(type, rowNumber);
    }
}
