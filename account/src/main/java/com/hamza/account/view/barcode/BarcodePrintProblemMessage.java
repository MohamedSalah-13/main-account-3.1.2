package com.hamza.account.view.barcode;

import com.hamza.account.features.barcodeprint.BarcodePrintProblem;
import com.hamza.account.features.barcodeprint.BarcodePrintValidation;
import com.hamza.controlsfx.language.LanguageManager;

/** The sentence a barcode-print problem is shown with, shared by every screen that prints labels. */
public final class BarcodePrintProblemMessage {

    private BarcodePrintProblemMessage() {
    }

    public static String of(BarcodePrintProblem problem) {
        LanguageManager language = LanguageManager.getInstance();
        return switch (problem.type()) {
            case EMPTY_BATCH -> language.getString("barcode.print.validation.empty");
            case MISSING_PRINTER -> language.getString("barcode.print.validation.printer.required");
            case MISSING_BARCODE -> language.getString("barcode.print.validation.barcode", problem.rowNumber());
            case INVALID_COPIES -> problem.rowNumber() > 0
                    ? language.getString("barcode.print.validation.copies", problem.rowNumber(),
                    BarcodePrintValidation.MAX_COPIES_PER_LINE)
                    : language.getString("barcode.print.validation.copies.all",
                    BarcodePrintValidation.MAX_COPIES_PER_LINE);
            case INVALID_PRICE -> language.getString("barcode.print.validation.price", problem.rowNumber());
            case INVALID_LABEL_SIZE -> language.getString("barcode.print.validation.size");
            case INVALID_NAME_SETTINGS -> language.getString("barcode.print.validation.name.settings");
            case UNSUPPORTED_BARCODE -> language.getString("barcode.print.validation.barcode.unsupported",
                    problem.rowNumber());
            case BARCODE_TOO_WIDE -> language.getString("barcode.print.validation.barcode.too.wide",
                    problem.rowNumber());
        };
    }
}
