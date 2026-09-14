package com.hamza.account.features.barcodeprint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BarcodePrinterProfileTest {

    @Test
    void recognizesTheInstalledXprinterModelRegardlessOfCaseOrExtraWhitespace() {
        BarcodePrinterProfile profile = BarcodePrinterProfile.forPrinter("  XPRINTER   XP-330B ");

        assertEquals(203, profile.dpi());
        assertEquals(76, profile.maximumPrintWidthMm());
    }

    @Test
    void leavesAnUnknownPrinterOnTheGenericProfile() {
        assertEquals(BarcodePrinterProfile.GENERIC,
                BarcodePrinterProfile.forPrinter("Office laser printer"));
    }
}
