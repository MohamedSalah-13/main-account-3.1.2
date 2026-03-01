package com.hamza.controlsfx.print;

import javafx.print.Printer;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class CheckPrinterSetting {

    /**
     * Constant representing the name of the Microsoft Print to PDF printer.
     * This value can be used when specifying or checking the printer configuration.
     */
    private static final String MICROSOFT_PRINT_TO_PDF = "Microsoft Print to PDF";

    /**
     * Checks the specified printer by its name and handles it accordingly. If the printer exists,
     * it processes the printer; otherwise, it saves the default or a PDF printer.
     *
     * @param namePrinter the name of the printer to check
     * @return always returns "Microsoft Print to PDF"
     */
    public static String checkPrinter(@NotNull String namePrinter) {
        Optional<Printer> printerOptional = Printer.getAllPrinters()
                .stream()
                .filter(printer -> printer.getName().equals(namePrinter))
                .findFirst();

        if (printerOptional.isPresent()) {
            return namePrinter;
        }
        return MICROSOFT_PRINT_TO_PDF;
    }

}
