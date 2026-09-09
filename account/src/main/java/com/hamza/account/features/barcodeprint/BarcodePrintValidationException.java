package com.hamza.account.features.barcodeprint;

import java.util.List;

/** Carries validation facts to the presentation boundary without constructing user-facing text. */
public final class BarcodePrintValidationException extends Exception {
    private final List<BarcodePrintProblem> problems;

    public BarcodePrintValidationException(List<BarcodePrintProblem> problems) {
        super("barcode.print.validation.failed");
        this.problems = List.copyOf(problems);
    }

    public List<BarcodePrintProblem> problems() {
        return problems;
    }
}
