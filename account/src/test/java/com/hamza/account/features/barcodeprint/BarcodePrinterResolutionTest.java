package com.hamza.account.features.barcodeprint;

import org.junit.jupiter.api.Test;

import javax.print.attribute.standard.PrinterResolution;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BarcodePrinterResolutionTest {

    @Test
    void takesTheDensityTheDriverReports() {
        // What the Xprinter XP-330B driver answers: 20300 dots per hundred inches.
        assertEquals(203, BarcodePrinterResolution.choose(
                new PrinterResolution(203, 203, PrinterResolution.DPI), null));
    }

    @Test
    void fallsBackToTheFirstSupportedDensityWhenThereIsNoDefault() {
        assertEquals(300, BarcodePrinterResolution.choose(null, new PrinterResolution[]{
                new PrinterResolution(300, 300, PrinterResolution.DPI)}));
    }

    @Test
    void assumesAThermalLabelHeadWhenNothingIsKnown() {
        assertEquals(203, BarcodePrinterResolution.choose(null, null));
        assertEquals(203, BarcodePrinterResolution.of(null));
    }
}
