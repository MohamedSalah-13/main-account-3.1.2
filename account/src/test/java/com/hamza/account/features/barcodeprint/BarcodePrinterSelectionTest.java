package com.hamza.account.features.barcodeprint;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarcodePrinterSelectionTest {

    @Test
    void initialNullSelectionIsNotReadyAndDoesNotFailForAnImmutableList() {
        assertFalse(BarcodePrinterSelection.isAvailable(List.of(), null));
    }

    @Test
    void requiresAnExactNonBlankPrinterMatch() {
        List<String> printers = List.of("Barcode Printer", "Office Printer");

        assertTrue(BarcodePrinterSelection.isAvailable(printers, "Barcode Printer"));
        assertFalse(BarcodePrinterSelection.isAvailable(printers, "barcode printer"));
        assertFalse(BarcodePrinterSelection.isAvailable(printers, " "));
    }
}
