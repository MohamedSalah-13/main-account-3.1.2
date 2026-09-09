package com.hamza.account.features.barcodeprint;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Pure validation shared by preview and print, with no JavaFX or localized strings. */
public final class BarcodePrintValidation {
    public static final int MAX_COPIES_PER_LINE = 10_000;
    public static final double MIN_LABEL_MM = 10;
    public static final double MAX_LABEL_MM = 300;

    private BarcodePrintValidation() {
    }

    public static List<BarcodePrintProblem> forPreview(BarcodePrintBatch batch) {
        return validate(batch, false);
    }

    public static List<BarcodePrintProblem> forPrint(BarcodePrintBatch batch) {
        return validate(batch, true);
    }

    private static List<BarcodePrintProblem> validate(BarcodePrintBatch batch, boolean printerRequired) {
        var problems = new ArrayList<BarcodePrintProblem>();
        if (batch == null || batch.lines().isEmpty()) {
            problems.add(BarcodePrintProblem.batch(BarcodePrintProblem.Type.EMPTY_BATCH));
            return List.copyOf(problems);
        }
        if (printerRequired && batch.printerName().isBlank()) {
            problems.add(BarcodePrintProblem.batch(BarcodePrintProblem.Type.MISSING_PRINTER));
        }
        validateOptions(batch.options(), problems);
        for (int index = 0; index < batch.lines().size(); index++) {
            BarcodePrintLine line = batch.lines().get(index);
            int rowNumber = index + 1;
            if (line == null || line.barcode().isBlank()) {
                problems.add(BarcodePrintProblem.row(BarcodePrintProblem.Type.MISSING_BARCODE, rowNumber));
                continue;
            }
            if (line.copies() < 1 || line.copies() > MAX_COPIES_PER_LINE) {
                problems.add(BarcodePrintProblem.row(BarcodePrintProblem.Type.INVALID_COPIES, rowNumber));
            }
            if (line.price().compareTo(BigDecimal.ZERO) < 0) {
                problems.add(BarcodePrintProblem.row(BarcodePrintProblem.Type.INVALID_PRICE, rowNumber));
            }
        }
        return List.copyOf(problems);
    }

    private static void validateOptions(BarcodeLabelOptions options, List<BarcodePrintProblem> problems) {
        if (options == null
                || !Double.isFinite(options.widthMm()) || !Double.isFinite(options.heightMm())
                || options.widthMm() < MIN_LABEL_MM || options.widthMm() > MAX_LABEL_MM
                || options.heightMm() < MIN_LABEL_MM || options.heightMm() > MAX_LABEL_MM) {
            problems.add(BarcodePrintProblem.batch(BarcodePrintProblem.Type.INVALID_LABEL_SIZE));
            return;
        }
        if (options.nameOverflow() == null
                || options.nameMaximumCharacters() < 1 || options.nameMaximumCharacters() > 200
                || options.nameFontSize() < 4 || options.nameFontSize() > 30) {
            problems.add(BarcodePrintProblem.batch(BarcodePrintProblem.Type.INVALID_NAME_SETTINGS));
        }
    }
}
