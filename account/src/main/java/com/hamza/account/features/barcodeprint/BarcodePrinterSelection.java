package com.hamza.account.features.barcodeprint;

import java.util.List;

/** Pure readiness check for the exact printer selected by the operator. */
public final class BarcodePrinterSelection {
    private BarcodePrinterSelection() {
    }

    public static boolean isAvailable(List<String> availablePrinters, String selectedPrinter) {
        return selectedPrinter != null
                && !selectedPrinter.isBlank()
                && availablePrinters != null
                && availablePrinters.contains(selectedPrinter);
    }
}
