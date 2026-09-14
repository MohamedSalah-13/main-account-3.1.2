package com.hamza.account.features.barcodeprint;

/** Physical offsets for one printer, in millimetres, applied only when a label is printed. */
public record BarcodePrintCalibration(double horizontalOffsetMm, double verticalOffsetMm) {
    public static final BarcodePrintCalibration NONE = new BarcodePrintCalibration(0, 0);

    public BarcodePrintCalibration {
        horizontalOffsetMm = finiteOrZero(horizontalOffsetMm);
        verticalOffsetMm = finiteOrZero(verticalOffsetMm);
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0;
    }
}
